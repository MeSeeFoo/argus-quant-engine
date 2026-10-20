package com.argus.feedgateway.adapter.out.aeron;

import com.argus.contracts.sbe.BestBidAskEncoder;
import com.argus.contracts.sbe.HeartbeatEncoder;
import com.argus.contracts.sbe.MessageHeaderEncoder;
import com.argus.contracts.sbe.Side;
import com.argus.contracts.sbe.TradeEncoder;
import com.argus.feedgateway.application.MarketDataPublisher;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.Instrument;
import io.aeron.ExclusivePublication;
import io.aeron.Publication;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Publishes canonical {@code Trade}, {@code BestBidAsk} and {@code Heartbeat} messages on the
 * gateway's Aeron stream.
 *
 * <ul>
 *   <li>{@code Trade} and {@code BestBidAsk} share one {@code sequence}, starting at 1. A message
 *       takes its sequence even when it is dropped, so subscribers see the loss as a gap.
 *   <li>It never blocks: when Aeron refuses a message (no subscriber, back-pressure…) the message
 *       is dropped and counted. The only retry is a single one after a term rotation, which is not
 *       a refusal.
 *   <li>{@link #heartbeatIfIdle} sends a {@code Heartbeat} with the last sequence when nothing was
 *       published during a whole interval.
 * </ul>
 *
 * <p>Single-threaded: one thread calls every method. Allocation-free.
 */
public final class AeronMarketDataPublisher implements MarketDataPublisher {

  private static final int MAX_MESSAGE_LENGTH = 128;

  private final ExclusivePublication publication;
  private final PublicationCounters counters;
  private final long heartbeatIntervalNanos;

  private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[MAX_MESSAGE_LENGTH]);
  private final MessageHeaderEncoder header = new MessageHeaderEncoder();
  private final TradeEncoder trade = new TradeEncoder();
  private final BestBidAskEncoder bestBidAsk = new BestBidAskEncoder();
  private final HeartbeatEncoder heartbeat = new HeartbeatEncoder();

  private long sequence;
  private long nextHeartbeatCheck;
  // False until the first check, which sends a heartbeat straight away
  private boolean heartbeatScheduled;
  private boolean publishedSinceLastCheck;

  public AeronMarketDataPublisher(
      ExclusivePublication publication, PublicationCounters counters, long heartbeatIntervalNanos) {
    if (heartbeatIntervalNanos <= 0) {
      throw new IllegalArgumentException("heartbeat interval must be positive");
    }
    this.publication = publication;
    this.counters = counters;
    this.heartbeatIntervalNanos = heartbeatIntervalNanos;
  }

  @Override
  public void publishTrade(
      Instrument instrument,
      long tradeId,
      long price,
      long quantity,
      long exchangeTimestamp,
      long receiveTimestamp,
      AggressorSide aggressorSide) {
    trade
        .wrapAndApplyHeader(buffer, 0, header)
        .sequence(++sequence)
        .tradeId(tradeId)
        .exchangeTimestamp(exchangeTimestamp)
        .receiveTimestamp(receiveTimestamp)
        .aggressorSide(aggressorSide == AggressorSide.BUY ? Side.BUY : Side.SELL)
        // As a CharSequence: copied byte by byte, without allocating
        .venue((CharSequence) instrument.venue())
        .symbol((CharSequence) instrument.symbol());
    trade.price().mantissa(price);
    trade.quantity().mantissa(quantity);
    publishedSinceLastCheck = true;
    if (offer(MessageHeaderEncoder.ENCODED_LENGTH + trade.encodedLength())) {
      counters.countTrade();
    }
  }

  @Override
  public void publishBestBidAsk(
      Instrument instrument,
      long bookUpdateId,
      long bidPrice,
      long bidQuantity,
      long askPrice,
      long askQuantity,
      long exchangeTimestamp,
      long receiveTimestamp) {
    bestBidAsk
        .wrapAndApplyHeader(buffer, 0, header)
        .sequence(++sequence)
        .bookUpdateId(bookUpdateId)
        .exchangeTimestamp(exchangeTimestamp)
        .receiveTimestamp(receiveTimestamp)
        .venue((CharSequence) instrument.venue())
        .symbol((CharSequence) instrument.symbol());
    bestBidAsk.bidPrice().mantissa(bidPrice);
    bestBidAsk.bidQuantity().mantissa(bidQuantity);
    bestBidAsk.askPrice().mantissa(askPrice);
    bestBidAsk.askQuantity().mantissa(askQuantity);
    publishedSinceLastCheck = true;
    if (offer(MessageHeaderEncoder.ENCODED_LENGTH + bestBidAsk.encodedLength())) {
      counters.countBestBidAsk();
    }
  }

  /**
   * Called on every duty cycle of the publishing thread. Sends a {@code Heartbeat} when nothing was
   * published since the previous check, at most once per interval, so an idle stream carries one
   * every one to two intervals.
   *
   * @param monotonicNow a monotonic clock in nanoseconds ({@link System#nanoTime()}), which
   *     schedules the checks: a wall clock stepping backwards must not silence the heartbeat
   * @param epochNow sender clock, epoch nanoseconds, written in the message: liveness only, never
   *     event time
   * @return 1 if a heartbeat was sent, 0 otherwise (the agent's work count)
   */
  public int heartbeatIfIdle(long monotonicNow, long epochNow) {
    // Compared by difference: nanoTime may be negative, and may wrap
    if (heartbeatScheduled && monotonicNow - nextHeartbeatCheck < 0) {
      return 0;
    }
    heartbeatScheduled = true;
    nextHeartbeatCheck = monotonicNow + heartbeatIntervalNanos;
    if (publishedSinceLastCheck) {
      publishedSinceLastCheck = false;
      return 0;
    }
    heartbeat.wrapAndApplyHeader(buffer, 0, header).sequence(sequence).timestamp(epochNow);
    if (offer(MessageHeaderEncoder.ENCODED_LENGTH + heartbeat.encodedLength())) {
      counters.countHeartbeat();
    }
    return 1;
  }

  private boolean offer(int length) {
    long result = publication.offer(buffer, 0, length);
    if (result == Publication.ADMIN_ACTION) {
      // The offer reached the end of a term and rotated it without writing the message. One
      // immediate retry writes it in the new term: bounded, so it never blocks.
      result = publication.offer(buffer, 0, length);
    }
    if (result > 0) {
      return true;
    }
    if (result == Publication.NOT_CONNECTED) {
      counters.countDroppedNotConnected();
    } else if (result == Publication.BACK_PRESSURED) {
      counters.countDroppedBackPressured();
    } else if (result == Publication.CLOSED || result == Publication.MAX_POSITION_EXCEEDED) {
      counters.countDroppedClosed();
    } else {
      counters.countDroppedOther();
    }
    return false;
  }
}
