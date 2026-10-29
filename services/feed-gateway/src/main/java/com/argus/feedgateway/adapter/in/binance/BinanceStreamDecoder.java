package com.argus.feedgateway.adapter.in.binance;

import com.argus.feedgateway.adapter.in.binance.sbe.BestBidAskStreamEventDecoder;
import com.argus.feedgateway.adapter.in.binance.sbe.BoolEnum;
import com.argus.feedgateway.adapter.in.binance.sbe.GroupSizeEncodingDecoder;
import com.argus.feedgateway.adapter.in.binance.sbe.MessageHeaderDecoder;
import com.argus.feedgateway.adapter.in.binance.sbe.TradesStreamEventDecoder;
import com.argus.feedgateway.adapter.in.binance.sbe.TradesStreamEventDecoder.TradesDecoder;
import com.argus.feedgateway.application.port.in.IngestMarketData;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Decodes the binary frames of Binance's SBE market data stream ({@code <symbol>@trade} and {@code
 * <symbol>@bestBidAsk}) with flyweights, fills one reused {@link Trade} or {@link BestBidAsk} per
 * event and hands it to the {@link IngestMarketData} input port. Other messages, other schemas and
 * malformed frames are counted and ignored. Runs on the connection's thread: one instance per
 * connection, not thread-safe, and allocation-free per frame.
 */
public final class BinanceStreamDecoder {

  private static final long NANOS_PER_MICRO = 1_000;
  private static final int ROOT_OFFSET = MessageHeaderDecoder.ENCODED_LENGTH;

  private final MessageHeaderDecoder header = new MessageHeaderDecoder();
  private final TradesStreamEventDecoder trades = new TradesStreamEventDecoder();
  private final BestBidAskStreamEventDecoder bestBidAsk = new BestBidAskStreamEventDecoder();
  private final UnsafeBuffer frame = new UnsafeBuffer(0, 0);
  // Longer than any symbol of the shard, so a longer symbol never matches
  private final byte[] symbol = new byte[Instrument.MAX_SYMBOL_LENGTH + 1];

  private final Instruments instruments;
  private final IngestMarketData ingest;
  private final BinanceCounters counters;
  private final Trade trade = new Trade();
  private final BestBidAsk book = new BestBidAsk();

  public BinanceStreamDecoder(
      Instruments instruments, IngestMarketData ingest, BinanceCounters counters) {
    this.instruments = instruments;
    this.ingest = ingest;
    this.counters = counters;
  }

  /**
   * Decodes one frame.
   *
   * @param receiveTimestamp time the gateway received the frame, epoch nanoseconds
   */
  public void onFrame(DirectBuffer buffer, int offset, int length, long receiveTimestamp) {
    if (length < ROOT_OFFSET) {
      counters.unsupportedMessage();
      return;
    }
    // A view of exactly this frame. Every length read from the wire is checked against it before
    // it is used, so a malformed frame is rejected instead of read past its end.
    frame.wrap(buffer, offset, length);
    header.wrap(frame, 0);
    if (header.schemaId() != TradesStreamEventDecoder.SCHEMA_ID) {
      counters.unsupportedMessage();
      return;
    }
    switch (header.templateId()) {
      case TradesStreamEventDecoder.TEMPLATE_ID -> onTrades(receiveTimestamp);
      case BestBidAskStreamEventDecoder.TEMPLATE_ID -> onBestBidAsk(receiveTimestamp);
      default -> counters.unsupportedMessage();
    }
  }

  private void onTrades(long receiveTimestamp) {
    int blockLength = header.blockLength();
    if (blockLength < TradesStreamEventDecoder.BLOCK_LENGTH
        || !fits(ROOT_OFFSET + blockLength + GroupSizeEncodingDecoder.ENCODED_LENGTH)) {
      counters.unsupportedMessage();
      return;
    }
    trades.wrap(frame, ROOT_OFFSET, blockLength, header.version());
    // The symbol comes after the trades: check and skip them to read it, then rewind.
    TradesDecoder group = trades.trades();
    int count = group.count();
    int entryLength = group.actingBlockLength();
    if (count < 0
        || entryLength < TradesDecoder.sbeBlockLength()
        || !fits(
            trades.limit()
                + (long) count * entryLength
                + TradesStreamEventDecoder.symbolHeaderLength())) {
      counters.unsupportedMessage();
      return;
    }
    while (group.hasNext()) {
      short buyerIsMaker = group.next().isBuyerMakerRaw();
      if (buyerIsMaker != BoolEnum.True.value() && buyerIsMaker != BoolEnum.False.value()) {
        counters.unsupportedMessage();
        return;
      }
    }
    if (!fits(
        trades.limit() + TradesStreamEventDecoder.symbolHeaderLength() + trades.symbolLength())) {
      counters.unsupportedMessage();
      return;
    }
    int instrument = instrumentOf(trades.getSymbol(symbol, 0, symbol.length));
    if (instrument == Instruments.UNKNOWN) {
      return;
    }
    trades.sbeRewind();
    long exchangeTimestamp = trades.transactTime() * NANOS_PER_MICRO;
    byte priceExponent = trades.priceExponent();
    byte quantityExponent = trades.qtyExponent();
    group = trades.trades();
    while (group.hasNext()) {
      group.next();
      trade
          .instrument(instruments.get(instrument))
          .tradeId(group.id())
          .price(group.price(), priceExponent)
          .quantity(group.qty(), quantityExponent)
          // The maker's order was resting in the book; the other side crossed the spread.
          .aggressorSide(
              group.isBuyerMakerRaw() == BoolEnum.True.value()
                  ? AggressorSide.SELL
                  : AggressorSide.BUY)
          .exchangeTimestamp(exchangeTimestamp)
          .receiveTimestamp(receiveTimestamp);
      ingest.onTrade(trade);
    }
  }

  private void onBestBidAsk(long receiveTimestamp) {
    int blockLength = header.blockLength();
    if (blockLength < BestBidAskStreamEventDecoder.BLOCK_LENGTH
        || !fits(ROOT_OFFSET + blockLength + BestBidAskStreamEventDecoder.symbolHeaderLength())) {
      counters.unsupportedMessage();
      return;
    }
    bestBidAsk.wrap(frame, ROOT_OFFSET, blockLength, header.version());
    if (!fits(
        bestBidAsk.limit()
            + BestBidAskStreamEventDecoder.symbolHeaderLength()
            + bestBidAsk.symbolLength())) {
      counters.unsupportedMessage();
      return;
    }
    long eventTime = bestBidAsk.eventTime();
    long bookUpdateId = bestBidAsk.bookUpdateId();
    byte priceExponent = bestBidAsk.priceExponent();
    byte quantityExponent = bestBidAsk.qtyExponent();
    long bidPrice = bestBidAsk.bidPrice();
    long bidQuantity = bestBidAsk.bidQty();
    long askPrice = bestBidAsk.askPrice();
    long askQuantity = bestBidAsk.askQty();
    int instrument = instrumentOf(bestBidAsk.getSymbol(symbol, 0, symbol.length));
    if (instrument == Instruments.UNKNOWN) {
      return;
    }
    book.instrument(instruments.get(instrument))
        .bookUpdateId(bookUpdateId)
        .bid(bidPrice, priceExponent, bidQuantity, quantityExponent)
        .ask(askPrice, priceExponent, askQuantity, quantityExponent)
        .exchangeTimestamp(eventTime * NANOS_PER_MICRO)
        .receiveTimestamp(receiveTimestamp);
    ingest.onBestBidAsk(book);
  }

  private int instrumentOf(int symbolLength) {
    int instrument = instruments.indexOf(symbol, symbolLength);
    if (instrument == Instruments.UNKNOWN) {
      counters.unknownSymbol();
    }
    return instrument;
  }

  /** Whether a part of the message ending at {@code end} lies inside the frame. */
  private boolean fits(long end) {
    return end <= frame.capacity();
  }
}
