package com.argus.modelengine.adapter.in.aeron;

import com.argus.contracts.sbe.BestBidAskDecoder;
import com.argus.contracts.sbe.HeartbeatDecoder;
import com.argus.contracts.sbe.MessageHeaderDecoder;
import com.argus.contracts.sbe.Side;
import com.argus.contracts.sbe.TradeDecoder;
import com.argus.modelengine.application.port.in.IngestMarketData;
import com.argus.modelengine.application.port.in.MarketDataSource;
import com.argus.modelengine.domain.AggressorSide;
import com.argus.modelengine.domain.BestBidAsk;
import com.argus.modelengine.domain.Instrument;
import com.argus.modelengine.domain.Instruments;
import com.argus.modelengine.domain.Trade;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Reads the gateway's canonical SBE messages from an Aeron subscription and hands them to {@link
 * IngestMarketData}.
 *
 * <p>Every message is shorter than the MTU, so one fragment is one message and the poll's limit
 * counts messages.
 *
 * <p>Used only on the reader thread. Allocation-free: decoders and views are reused, and the events
 * it hands over read straight from Aeron's buffer, so they are valid only during the call.
 */
public final class AeronMarketDataSource implements MarketDataSource {

  private final Subscription subscription;
  private final Instruments instruments;
  private final IngestMarketData ingest;
  private final AeronInputCounters counters = new AeronInputCounters();

  // Created once: a method reference made at each poll would allocate
  private final FragmentHandler fragmentHandler = this::onFragment;
  private final MessageHeaderDecoder messageHeader = new MessageHeaderDecoder();
  private final TradeDecoder tradeDecoder = new TradeDecoder();
  private final BestBidAskDecoder bestBidAskDecoder = new BestBidAskDecoder();
  private final HeartbeatDecoder heartbeatDecoder = new HeartbeatDecoder();
  private final DecodedTrade trade = new DecodedTrade(tradeDecoder);
  private final DecodedBestBidAsk bestBidAsk = new DecodedBestBidAsk(bestBidAskDecoder);
  private final byte[] venue = new byte[Instrument.VENUE_LENGTH];
  private final byte[] symbol = new byte[Instrument.SYMBOL_LENGTH];

  /**
   * Creates the source.
   *
   * @param subscription the gateway's stream
   * @param instruments the instruments the engine handles
   * @param ingest receives every message read
   */
  public AeronMarketDataSource(
      Subscription subscription, Instruments instruments, IngestMarketData ingest) {
    this.subscription = subscription;
    this.instruments = instruments;
    this.ingest = ingest;
  }

  @Override
  public int poll(int limit) {
    return subscription.poll(fragmentHandler, limit);
  }

  /**
   * What this adapter read but did not hand over.
   *
   * @return its counters
   */
  public AeronInputCounters counters() {
    return counters;
  }

  /**
   * Decodes one message.
   *
   * @param buffer the buffer holding it
   * @param offset where it starts
   * @param length its length in bytes
   * @param header Aeron's header, which names the publisher session
   */
  private void onFragment(DirectBuffer buffer, int offset, int length, Header header) {
    messageHeader.wrap(buffer, offset);
    if (messageHeader.schemaId() != MessageHeaderDecoder.SCHEMA_ID) {
      counters.countUnsupported();
      return;
    }
    switch (messageHeader.templateId()) {
      case TradeDecoder.TEMPLATE_ID -> onTrade(buffer, offset, header.sessionId());
      case BestBidAskDecoder.TEMPLATE_ID -> onBestBidAsk(buffer, offset, header.sessionId());
      case HeartbeatDecoder.TEMPLATE_ID -> onHeartbeat(buffer, offset, header.sessionId());
      default -> counters.countUnsupported();
    }
  }

  /**
   * Decodes a trade and hands it over, or reports it as ignored.
   *
   * @param buffer the buffer holding it
   * @param offset where its header starts
   * @param session the publisher session
   */
  private void onTrade(DirectBuffer buffer, int offset, int session) {
    tradeDecoder.wrapAndApplyHeader(buffer, offset, messageHeader);
    tradeDecoder.getVenue(venue, 0);
    tradeDecoder.getSymbol(symbol, 0);
    int instrumentId = instruments.indexOf(venue, symbol);
    AggressorSide side = aggressorSide(tradeDecoder.aggressorSideRaw());
    if (instrumentId == Instruments.UNKNOWN) {
      counters.countUnknownInstrument();
      ingest.onIgnored(session, tradeDecoder.sequence());
    } else if (side == null) {
      counters.countMalformed();
      ingest.onIgnored(session, tradeDecoder.sequence());
    } else {
      trade.set(instrumentId, side);
      ingest.onTrade(session, trade);
    }
  }

  /**
   * Maps the wire value of a trade's side. The raw value, because decoding an enum throws on a
   * value it does not know.
   *
   * @param raw the value on the wire
   * @return its side, or {@code null} if it is not a valid one
   */
  private static @Nullable AggressorSide aggressorSide(short raw) {
    if (raw == Side.BUY.value()) {
      return AggressorSide.BUY;
    }
    if (raw == Side.SELL.value()) {
      return AggressorSide.SELL;
    }
    return null;
  }

  /**
   * Decodes a top-of-book update and hands it over, or reports it as ignored.
   *
   * @param buffer the buffer holding it
   * @param offset where its header starts
   * @param session the publisher session
   */
  private void onBestBidAsk(DirectBuffer buffer, int offset, int session) {
    bestBidAskDecoder.wrapAndApplyHeader(buffer, offset, messageHeader);
    bestBidAskDecoder.getVenue(venue, 0);
    bestBidAskDecoder.getSymbol(symbol, 0);
    int instrumentId = instruments.indexOf(venue, symbol);
    if (instrumentId == Instruments.UNKNOWN) {
      counters.countUnknownInstrument();
      ingest.onIgnored(session, bestBidAskDecoder.sequence());
    } else {
      bestBidAsk.instrumentId = instrumentId;
      ingest.onBestBidAsk(session, bestBidAsk);
    }
  }

  /**
   * Decodes a heartbeat and hands it over.
   *
   * @param buffer the buffer holding it
   * @param offset where its header starts
   * @param session the publisher session
   */
  private void onHeartbeat(DirectBuffer buffer, int offset, int session) {
    heartbeatDecoder.wrapAndApplyHeader(buffer, offset, messageHeader);
    ingest.onHeartbeat(session, heartbeatDecoder.sequence());
  }

  /** A read-only view of the trade the decoder is wrapped around. */
  private static final class DecodedTrade implements Trade {

    private final TradeDecoder decoder;
    private int instrumentId;
    private AggressorSide side = AggressorSide.BUY;

    /**
     * Creates a view over a decoder.
     *
     * @param decoder the decoder it reads from
     */
    DecodedTrade(TradeDecoder decoder) {
      this.decoder = decoder;
    }

    /**
     * Sets what the decoder does not give directly.
     *
     * @param instrumentId the engine's id of the instrument
     * @param side the trade's side
     */
    void set(int instrumentId, AggressorSide side) {
      this.instrumentId = instrumentId;
      this.side = side;
    }

    @Override
    public int instrumentId() {
      return instrumentId;
    }

    @Override
    public long sequence() {
      return decoder.sequence();
    }

    @Override
    public long exchangeTimestamp() {
      return decoder.exchangeTimestamp();
    }

    @Override
    public long receiveTimestamp() {
      return decoder.receiveTimestamp();
    }

    @Override
    public long tradeId() {
      return decoder.tradeId();
    }

    @Override
    public long price() {
      return decoder.price().mantissa();
    }

    @Override
    public long quantity() {
      return decoder.quantity().mantissa();
    }

    @Override
    public AggressorSide aggressorSide() {
      return side;
    }
  }

  /** A read-only view of the top-of-book update the decoder is wrapped around. */
  private static final class DecodedBestBidAsk implements BestBidAsk {

    private final BestBidAskDecoder decoder;
    private int instrumentId;

    /**
     * Creates a view over a decoder.
     *
     * @param decoder the decoder it reads from
     */
    DecodedBestBidAsk(BestBidAskDecoder decoder) {
      this.decoder = decoder;
    }

    @Override
    public int instrumentId() {
      return instrumentId;
    }

    @Override
    public long sequence() {
      return decoder.sequence();
    }

    @Override
    public long exchangeTimestamp() {
      return decoder.exchangeTimestamp();
    }

    @Override
    public long receiveTimestamp() {
      return decoder.receiveTimestamp();
    }

    @Override
    public long bookUpdateId() {
      return decoder.bookUpdateId();
    }

    @Override
    public long bidPrice() {
      return decoder.bidPrice().mantissa();
    }

    @Override
    public long bidQuantity() {
      return decoder.bidQuantity().mantissa();
    }

    @Override
    public long askPrice() {
      return decoder.askPrice().mantissa();
    }

    @Override
    public long askQuantity() {
      return decoder.askQuantity().mantissa();
    }
  }
}
