package com.argus.modelengine.application;

import com.argus.modelengine.application.port.in.IngestMarketData;
import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.Trade;
import com.lmax.disruptor.EventPoller;
import com.lmax.disruptor.EventTranslatorOneArg;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.YieldingWaitStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The reader's use case: checks each event an inbound adapter hands over, and publishes the ones
 * accepted into the Disruptor ring for the evaluator. Reader thread. Allocation-free.
 *
 * <p>The reader always publishes what it reads and never waits for the evaluator: when the ring is
 * full, the {@link BackpressureStrategy} decides what is lost (ADR 0029).
 */
public class MarketDataService implements IngestMarketData {

  private static final Logger LOG = LoggerFactory.getLogger(MarketDataService.class);

  private final MarketDataEventChecker marketDataEventChecker;
  private final RingBuffer<MarketDataSlot> marketDataRingBuffer;
  private final RingOverflowMechanism ringOverflowMechanism;

  private static final EventTranslatorOneArg<MarketDataSlot, Trade> TRADE_TRANSLATOR =
      (slot, sequence, trade) -> slot.setTrade(trade);
  private static final EventTranslatorOneArg<MarketDataSlot, BestBidAsk> BEST_BID_ASK_TRANSLATOR =
      (slot, sequence, bestBidAsk) -> slot.setBestBidAsk(bestBidAsk);

  /**
   * Creates the service and its ring, with every slot allocated up front. Startup only.
   *
   * @param marketDataEventChecker the reader's checks, run before an event enters the ring
   * @param bufferSize the ring's number of slots, a power of two
   * @param backpressure what to drop when the ring is full
   * @param counters where what the ring loses is counted
   */
  public MarketDataService(
      MarketDataEventChecker marketDataEventChecker,
      int bufferSize,
      BackpressureStrategy backpressure,
      MarketDataCounters counters) {
    this.marketDataEventChecker = marketDataEventChecker;
    this.marketDataRingBuffer =
        RingBuffer.createSingleProducer(
            MarketDataSlot::new, bufferSize, new YieldingWaitStrategy());
    this.ringOverflowMechanism =
        new RingOverflowMechanism(backpressure, marketDataRingBuffer, counters);
    LOG.info(
        "Ring of {} slots between the reader and the evaluator; when it is full: {}",
        bufferSize,
        backpressure);
  }

  @Override
  public void onTrade(int session, Trade trade) {
    if (marketDataEventChecker.onTrade(session, trade)) {
      publish(TRADE_TRANSLATOR, trade);
    }
  }

  @Override
  public void onBestBidAsk(int session, BestBidAsk bestBidAsk) {
    if (marketDataEventChecker.onBestBidAsk(session, bestBidAsk)) {
      publish(BEST_BID_ASK_TRANSLATOR, bestBidAsk);
    }
  }

  @Override
  public void onIgnored(int session, long sequence) {
    marketDataEventChecker.onIgnored(session, sequence);
  }

  @Override
  public void onHeartbeat(int session, long lastPublished) {
    marketDataEventChecker.onHeartbeat(session, lastPublished);
  }

  /**
   * Creates the poller the evaluator drains the ring with, and makes the ring wait for it, so the
   * reader never overwrites a slot the evaluator has not read. Startup only, once.
   *
   * @return the poller
   */
  public EventPoller<MarketDataSlot> newPoller() {
    EventPoller<MarketDataSlot> poller = marketDataRingBuffer.newPoller();
    marketDataRingBuffer.addGatingSequences(poller.getSequence());
    return poller;
  }

  /**
   * What the ring does when it is full. The evaluator needs it to skip a backlog.
   *
   * @return the ring's overflow mechanism
   */
  public RingOverflowMechanism ringOverflowMechanism() {
    return ringOverflowMechanism;
  }

  /**
   * Publishes an event, or hands a full ring to the overflow mechanism. The reader is the ring's
   * only producer, so the evaluator can only free slots between the check and the claim: a claim
   * after a successful check never spins. Checking first also avoids {@code tryPublishEvent}, which
   * reports a full ring with an exception. Reader thread. Allocation-free.
   *
   * @param translator copies the event into a slot
   * @param event the event, valid only during this call
   * @param <E> the kind of event
   */
  private <E> void publish(EventTranslatorOneArg<MarketDataSlot, E> translator, E event) {
    if (marketDataRingBuffer.hasAvailableCapacity(1)) {
      marketDataRingBuffer.publishEvent(translator, event);
    } else {
      ringOverflowMechanism.onFull();
    }
  }
}
