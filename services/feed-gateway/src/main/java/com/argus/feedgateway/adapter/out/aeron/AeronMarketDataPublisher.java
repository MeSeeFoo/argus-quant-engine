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
  private final PublicationCounters counters;

  private final UnsafeBuffer buffer = new UnsafeBuffer(new byte[MAX_MESSAGE_LENGTH]);
  private final MessageHeaderEncoder header = new MessageHeaderEncoder();
  private final TradeEncoder tradeEncoder = new TradeEncoder();
  private final BestBidAskEncoder bestBidAskEncoder = new BestBidAskEncoder();
  private final HeartbeatEncoder heartbeatEncoder = new HeartbeatEncoder();

  private long sequence;

  public AeronMarketDataPublisher(ExclusivePublication publication, PublicationCounters counters) {
    this.publication = publication;
    this.counters = counters;
  }

  @Override
  public void publishTrade(Trade trade) {
    Instrument instrument = trade.instrument();
    tradeEncoder
        .wrapAndApplyHeader(buffer, 0, header)
        .sequence(++sequence)
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
      counters.countTrade();
    }
  }

  @Override
  public void publishBestBidAsk(BestBidAsk bestBidAsk) {
    Instrument instrument = bestBidAsk.instrument();
    bestBidAskEncoder
        .wrapAndApplyHeader(buffer, 0, header)
        .sequence(++sequence)
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
      counters.countBestBidAsk();
    }
  }

  @Override
  public void publishHeartbeat(long epochNow) {
    heartbeatEncoder.wrapAndApplyHeader(buffer, 0, header).sequence(sequence).timestamp(epochNow);
    if (offer(MessageHeaderEncoder.ENCODED_LENGTH + heartbeatEncoder.encodedLength())) {
      counters.countHeartbeat();
    }
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
