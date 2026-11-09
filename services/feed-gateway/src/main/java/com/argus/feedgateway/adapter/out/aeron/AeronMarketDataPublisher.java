package com.argus.feedgateway.adapter.out.aeron;

import com.argus.contracts.sbe.BestBidAskEncoder;
import com.argus.contracts.sbe.HeartbeatEncoder;
import com.argus.contracts.sbe.MessageHeaderEncoder;
import com.argus.contracts.sbe.Side;
import com.argus.contracts.sbe.TradeEncoder;
import com.argus.feedgateway.application.port.out.MarketDataPublisher;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Trade;
import io.aeron.ExclusivePublication;
import io.aeron.Publication;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Publishes canonical {@code Trade}, {@code BestBidAsk} and {@code Heartbeat} messages on the
 * gateway's Aeron stream.
 *
 * <ul>
 *   <li>{@code Trade} and {@code BestBidAsk} share one {@code sequence}, starting at 1. A message
 *       takes its sequence even when it is dropped, so subscribers see the loss as a gap. A {@code
 *       Heartbeat} carries the last sequence (0 before the first message).
 *   <li>It never blocks: when Aeron refuses a message (no subscriber, back-pressure…) the message
 *       is dropped and counted. The only retry is a single one after a term rotation, which is not
 *       a refusal.
 * </ul>
 *
 * <p>Single-threaded: one thread calls every method. Allocation-free.
 */
public final class AeronMarketDataPublisher implements MarketDataPublisher {

  private static final int MAX_MESSAGE_LENGTH = 128;

  private final ExclusivePublication publication;
  private final AeronCounters aeronCounters = new AeronCounters();

  private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[MAX_MESSAGE_LENGTH]);
  private final MessageHeaderEncoder header = new MessageHeaderEncoder();
  private final TradeEncoder tradeEncoder = new TradeEncoder();
  private final BestBidAskEncoder bestBidAskEncoder = new BestBidAskEncoder();
  private final HeartbeatEncoder heartbeatEncoder = new HeartbeatEncoder();

  /**
   * Creates a publisher on the shard's publication.
   *
   * @param publication the publication; only this publisher offers to it
   */
  public AeronMarketDataPublisher(ExclusivePublication publication) {
    this.publication = publication;
  }

  /**
   * Heartbeats and drops, which only Aeron counts.
   *
   * @return this publisher's counters
   */
  public AeronCounters aeronCounters() {
    return aeronCounters;
  }

  @Override
  public void publishTrade(Trade trade, long sequence) {
    Instrument instrument = trade.instrument();

    tradeEncoder
        .wrapAndApplyHeader(buffer, 0, header)
        .sequence(sequence)
        .tradeId(trade.tradeId())
        .exchangeTimestamp(trade.exchangeTimestamp())
        .receiveTimestamp(trade.receiveTimestamp())
        .aggressorSide(trade.aggressorSide() == AggressorSide.BUY ? Side.BUY : Side.SELL)
        // As a CharSequence: copied byte by byte, without allocating
        .venue((CharSequence) instrument.venue())
        .symbol((CharSequence) instrument.symbol());
    tradeEncoder.price().mantissa(trade.price());
    tradeEncoder.quantity().mantissa(trade.quantity());
    if (offer(MessageHeaderEncoder.ENCODED_LENGTH + tradeEncoder.encodedLength())) {
      aeronCounters.countTrade();
    }
  }

  @Override
  public void publishBestBidAsk(BestBidAsk bestBidAsk, long sequence) {
    Instrument instrument = bestBidAsk.instrument();
    bestBidAskEncoder
        .wrapAndApplyHeader(buffer, 0, header)
        .sequence(sequence)
        .bookUpdateId(bestBidAsk.bookUpdateId())
        .exchangeTimestamp(bestBidAsk.exchangeTimestamp())
        .receiveTimestamp(bestBidAsk.receiveTimestamp())
        .venue((CharSequence) instrument.venue())
        .symbol((CharSequence) instrument.symbol());
    bestBidAskEncoder.bidPrice().mantissa(bestBidAsk.bidPrice());
    bestBidAskEncoder.bidQuantity().mantissa(bestBidAsk.bidQuantity());
    bestBidAskEncoder.askPrice().mantissa(bestBidAsk.askPrice());
    bestBidAskEncoder.askQuantity().mantissa(bestBidAsk.askQuantity());
    if (offer(MessageHeaderEncoder.ENCODED_LENGTH + bestBidAskEncoder.encodedLength())) {
      aeronCounters.countBestBidAsk();
    }
  }

  @Override
  public void publishHeartbeat(long epochNow, long sequence) {
    heartbeatEncoder.wrapAndApplyHeader(buffer, 0, header).sequence(sequence).timestamp(epochNow);
    if (offer(MessageHeaderEncoder.ENCODED_LENGTH + heartbeatEncoder.encodedLength())) {
      aeronCounters.countHeartbeat();
    }
  }

  /**
   * Offers the encoded message to Aeron, counting a refusal. Never blocks; allocation-free.
   *
   * @param length the length of the message at the start of {@link #buffer}, header included
   * @return {@code true} if Aeron took it
   */
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
      aeronCounters.countDroppedNotConnected();
    } else if (result == Publication.BACK_PRESSURED) {
      aeronCounters.countDroppedBackPressured();
    } else if (result == Publication.CLOSED || result == Publication.MAX_POSITION_EXCEEDED) {
      aeronCounters.countDroppedClosed();
    } else {
      aeronCounters.countDroppedOther();
    }
    return false;
  }
}
