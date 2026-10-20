package com.argus.feedgateway.adapter.in.binance;

import com.argus.feedgateway.adapter.in.binance.sbe.BestBidAskStreamEventDecoder;
import com.argus.feedgateway.adapter.in.binance.sbe.BoolEnum;
import com.argus.feedgateway.adapter.in.binance.sbe.MessageHeaderDecoder;
import com.argus.feedgateway.adapter.in.binance.sbe.TradesStreamEventDecoder;
import com.argus.feedgateway.adapter.in.binance.sbe.TradesStreamEventDecoder.TradesDecoder;
import com.argus.feedgateway.application.MarketDataCounters;
import com.argus.feedgateway.application.MarketDataNormaliser;
import com.argus.feedgateway.domain.AggressorSide;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Decodes the binary frames of Binance's SBE market data stream ({@code <symbol>@trade} and {@code
 * <symbol>@bestBidAsk}) with flyweights, and passes them to the {@link MarketDataNormaliser}. Other
 * messages, other schemas and malformed frames are counted and ignored. One instance per
 * connection; not thread-safe, and allocation-free per frame.
 */
public final class BinanceStreamDecoder {

  private static final long NANOS_PER_MICRO = 1_000;

  private final MessageHeaderDecoder header = new MessageHeaderDecoder();
  private final TradesStreamEventDecoder trades = new TradesStreamEventDecoder();
  private final BestBidAskStreamEventDecoder bestBidAsk = new BestBidAskStreamEventDecoder();
  private final UnsafeBuffer frame = new UnsafeBuffer(0, 0);
  // Longer than any symbol of the shard, so a longer symbol never matches
  private final byte[] symbol = new byte[Instrument.MAX_SYMBOL_LENGTH + 1];

  private final Instruments instruments;
  private final MarketDataNormaliser normaliser;
  private final MarketDataCounters counters;

  public BinanceStreamDecoder(
      Instruments instruments, MarketDataNormaliser normaliser, MarketDataCounters counters) {
    this.instruments = instruments;
    this.normaliser = normaliser;
    this.counters = counters;
  }

  /**
   * Decodes one frame.
   *
   * @param receiveTimestamp time the gateway received the frame, epoch nanoseconds
   */
  public void onFrame(DirectBuffer buffer, int offset, int length, long receiveTimestamp) {
    if (length < MessageHeaderDecoder.ENCODED_LENGTH) {
      counters.unsupportedMessage();
      return;
    }
    // A view of exactly this frame: a truncated message fails the bounds checks instead of
    // reading past it.
    frame.wrap(buffer, offset, length);
    header.wrap(frame, 0);
    if (header.schemaId() != TradesStreamEventDecoder.SCHEMA_ID) {
      counters.unsupportedMessage();
      return;
    }
    try {
      switch (header.templateId()) {
        case TradesStreamEventDecoder.TEMPLATE_ID -> onTrades(receiveTimestamp);
        case BestBidAskStreamEventDecoder.TEMPLATE_ID -> onBestBidAsk(receiveTimestamp);
        default -> counters.unsupportedMessage();
      }
    } catch (IndexOutOfBoundsException malformed) {
      counters.unsupportedMessage();
    }
  }

  private void onTrades(long receiveTimestamp) {
    trades.wrap(frame, MessageHeaderDecoder.ENCODED_LENGTH, header.blockLength(), header.version());
    // The symbol comes after the trades: skip them to read it, then rewind.
    TradesDecoder group = trades.trades();
    while (group.hasNext()) {
      group.next();
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
      normaliser.onTrade(
          instrument,
          group.id(),
          group.price(),
          priceExponent,
          group.qty(),
          quantityExponent,
          // The maker's order was resting in the book; the other side crossed the spread.
          group.isBuyerMakerRaw() == BoolEnum.True.value() ? AggressorSide.SELL : AggressorSide.BUY,
          exchangeTimestamp,
          receiveTimestamp);
    }
  }

  private void onBestBidAsk(long receiveTimestamp) {
    bestBidAsk.wrap(
        frame, MessageHeaderDecoder.ENCODED_LENGTH, header.blockLength(), header.version());
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
    normaliser.onBestBidAsk(
        instrument,
        bookUpdateId,
        bidPrice,
        bidQuantity,
        askPrice,
        askQuantity,
        priceExponent,
        quantityExponent,
        eventTime * NANOS_PER_MICRO,
        receiveTimestamp);
  }

  private int instrumentOf(int symbolLength) {
    int instrument = instruments.indexOf(symbol, symbolLength);
    if (instrument == Instruments.UNKNOWN) {
      counters.unknownSymbol();
    }
    return instrument;
  }
}
