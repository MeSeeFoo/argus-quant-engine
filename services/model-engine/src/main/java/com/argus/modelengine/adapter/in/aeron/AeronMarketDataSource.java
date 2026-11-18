package com.argus.modelengine.adapter.in.aeron;

import com.argus.contracts.sbe.BestBidAskDecoder;
import com.argus.contracts.sbe.HeartbeatDecoder;
import com.argus.contracts.sbe.MessageHeaderDecoder;
import com.argus.contracts.sbe.TradeDecoder;
import com.argus.log.RateLimitedLog;
import com.argus.modelengine.adapter.in.aeron.decoder.DecodedBestBidAsk;
import com.argus.modelengine.adapter.in.aeron.decoder.DecodedTrade;
import com.argus.modelengine.application.port.in.IngestMarketData;
import com.argus.modelengine.domain.instrument.Instrument;
import com.argus.modelengine.domain.instrument.Instruments;
import com.argus.modelengine.domain.marketdata.AggressorSide;
import io.aeron.Subscription;
import io.aeron.logbuffer.FragmentHandler;
import io.aeron.logbuffer.Header;
import java.time.Duration;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads the gateway's canonical SBE messages from an Aeron subscription and hands them to {@link
 * IngestMarketData}. The adapter owns the reader agent that polls it, like the gateway's Kafka
 * sender: the application only sees what it hands over, through the input port.
 *
 * <p>Every message is shorter than the MTU, so one fragment is one message and the poll's limit
 * counts messages.
 *
 * <p>Used only on the reader thread. Allocation-free: decoders and views are reused, and the events
 * it hands over read straight from Aeron's buffer, so they are valid only during the call. A
 * message it ignores is counted and logged at most once per interval, which allocates only when a
 * line is written.
 */
public final class AeronMarketDataSource {

  /**
   * Messages read per duty cycle at most. It bounds one duty cycle, not the ring: during a burst
   * the agent polls again at once, and each message reaches the ring as soon as it is decoded, so
   * the value changes neither throughput nor latency. It keeps each cycle short, and fair between
   * the images of an active and a passive gateway, among which Aeron spreads the limit. The same as
   * the evaluator's batch limit, so both sides of the ring move in batches of the same size.
   */
  private static final int POLL_LIMIT = 256;

  private static final Logger LOG = LoggerFactory.getLogger(AeronMarketDataSource.class);
  // A misconfigured gateway may send them all the time: the counters count, the log samples
  private static final Duration IGNORED_LOG_INTERVAL = Duration.ofSeconds(10);
  private static final String UNKNOWN_INSTRUMENT =
      "Ignored a message of an instrument the engine does not handle";
  private static final String MALFORMED = "Ignored a trade with a side the engine does not know";
  private static final String UNSUPPORTED =
      "Ignored a message of another schema, or of a template the engine does not read";

  private final Subscription subscription;
  private final Instruments instruments;
  private final IngestMarketData ingestMarketDataService;
  private final AeronInputCounters counters = new AeronInputCounters();
  private final Agent agent = new AeronInputMDAgent();
  // One per kind, so a flood of one never hides the others; allocation-free while not logging
  private final RateLimitedLog unknownInstrumentLog = new RateLimitedLog(LOG, IGNORED_LOG_INTERVAL);
  private final RateLimitedLog malformedLog = new RateLimitedLog(LOG, IGNORED_LOG_INTERVAL);
  private final RateLimitedLog unsupportedLog = new RateLimitedLog(LOG, IGNORED_LOG_INTERVAL);

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
   * @param ingestMarketDataService receives every message read
   */
  public AeronMarketDataSource(
      Subscription subscription,
      Instruments instruments,
      IngestMarketData ingestMarketDataService) {
    this.subscription = subscription;
    this.instruments = instruments;
    this.ingestMarketDataService = ingestMarketDataService;
  }

  /**
   * Reads up to {@code limit} messages and hands each one to {@link IngestMarketData}. Never
   * blocks; the limit only bounds one call, and the next one reads the rest. Reader thread.
   * Allocation-free.
   *
   * @param limit the most messages to read, 0 or more
   * @return the number of messages read
   */
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
   * The reader agent that polls this source, for {@code AgentLifecycle} to run on its own thread.
   *
   * @return the agent, the same one every call
   */
  public Agent agent() {
    return agent;
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
      unsupportedLog.warn(UNSUPPORTED, counters.countUnsupported());
      return;
    }
    switch (messageHeader.templateId()) {
      case TradeDecoder.TEMPLATE_ID -> onTrade(buffer, offset, header.sessionId());
      case BestBidAskDecoder.TEMPLATE_ID -> onBestBidAsk(buffer, offset, header.sessionId());
      case HeartbeatDecoder.TEMPLATE_ID -> onHeartbeat(buffer, offset, header.sessionId());
      default -> unsupportedLog.warn(UNSUPPORTED, counters.countUnsupported());
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
    AggressorSide side = DecodedTrade.aggressorSide(tradeDecoder.aggressorSideRaw());
    if (instrumentId == Instruments.UNKNOWN) {
      unknownInstrumentLog.warn(UNKNOWN_INSTRUMENT, counters.countUnknownInstrument());
      ingestMarketDataService.onIgnored(session, tradeDecoder.sequence());
    } else if (side == null) {
      malformedLog.warn(MALFORMED, counters.countMalformed());
      ingestMarketDataService.onIgnored(session, tradeDecoder.sequence());
    } else {
      trade.set(instrumentId, side);
      ingestMarketDataService.onTrade(session, trade);
    }
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
      unknownInstrumentLog.warn(UNKNOWN_INSTRUMENT, counters.countUnknownInstrument());
      ingestMarketDataService.onIgnored(session, bestBidAskDecoder.sequence());
    } else {
      bestBidAsk.set(instrumentId);
      ingestMarketDataService.onBestBidAsk(session, bestBidAsk);
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
    ingestMarketDataService.onHeartbeat(session, heartbeatDecoder.sequence());
  }

  /**
   * The reader agent (ADR 0029): drains the subscription on its own thread, up to {@link
   * #POLL_LIMIT} messages per duty cycle so a burst never holds the thread for too long. It never
   * leaves data in Aeron for the evaluator's sake: a full ring is the backpressure strategy's
   * concern, in the application.
   */
  private final class AeronInputMDAgent implements Agent {

    @Override
    public int doWork() {
      return poll(POLL_LIMIT);
    }

    @Override
    public String roleName() {
      return "aeron-input-md";
    }
  }
}
