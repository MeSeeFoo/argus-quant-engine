package com.argus.modelengine;

import static com.argus.modelengine.domain.marketdata.MarketEventType.BEST_BID_ASK;
import static com.argus.modelengine.domain.marketdata.MarketEventType.TRADE;
import static org.assertj.core.api.Assertions.assertThat;

import com.argus.contracts.sbe.BestBidAskEncoder;
import com.argus.contracts.sbe.MessageHeaderEncoder;
import com.argus.contracts.sbe.Side;
import com.argus.contracts.sbe.TradeEncoder;
import com.argus.modelengine.adapter.in.aeron.AeronMarketDataSource;
import com.argus.modelengine.application.BackpressureStrategy;
import com.argus.modelengine.application.Evaluator;
import com.argus.modelengine.application.MarketDataCounters;
import com.argus.modelengine.application.MarketDataEventChecker;
import com.argus.modelengine.application.MarketDataService;
import com.argus.modelengine.domain.feed.Deduplicator;
import com.argus.modelengine.domain.feed.SequenceGapDetector;
import com.argus.modelengine.domain.instrument.Instrument;
import com.argus.modelengine.domain.instrument.Instruments;
import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.Trade;
import com.argus.modelengine.domain.model.DeployedModel;
import com.argus.modelengine.domain.model.Model;
import com.argus.modelengine.domain.model.ModelConfiguration;
import com.argus.modelengine.domain.model.ModelInputs;
import com.argus.modelengine.domain.window.WindowDefinition;
import io.aeron.Aeron;
import io.aeron.ExclusivePublication;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import java.lang.management.ManagementFactory;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.agrona.CloseHelper;
import org.agrona.concurrent.Agent;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The whole hot path must not allocate in steady state: decoding the gateway's SBE messages from
 * Aeron, the reader's checks, the hand-off through the Disruptor ring, the windows and the model's
 * evaluation (ADR 0029).
 *
 * <p>The test thread publishes to a real embedded Media Driver over IPC, then runs the reader agent
 * and the evaluator agent itself, so the JVM's per-thread allocation counter sees every step. The
 * driver's own threads allocate on their side and are not measured. Any allocation per message
 * would show up as hundreds of kilobytes over the loop, and the tolerance scales with the messages.
 */
class HotPathAllocationTest {

  private static final String CHANNEL = "aeron:ipc";
  private static final int STREAM_ID = 1001;
  private static final int MESSAGES = 200_000;
  private static final long TOLERATED_BYTES = MESSAGES / 100;
  // Published, then read and evaluated, before the next batch: the ring never fills
  private static final int BATCH = 128;
  private static final int RING_SIZE = 1024;
  private static final long DEADLINE_NANOS = TimeUnit.SECONDS.toNanos(30);

  private final Instruments instruments =
      new Instruments(List.of(Instrument.parse("BINANCE:BTCUSDT")));
  private final MarketDataCounters counters = new MarketDataCounters();
  private final SummingModel model = new SummingModel();
  private final MessageHeaderEncoder header = new MessageHeaderEncoder();
  private final UnsafeBuffer tradeBuffer = new UnsafeBuffer(new byte[128]);
  private final UnsafeBuffer bestBidAskBuffer = new UnsafeBuffer(new byte[128]);
  private final TradeEncoder trade = new TradeEncoder();
  private final BestBidAskEncoder bestBidAsk = new BestBidAskEncoder();
  private int tradeLength = 0;
  private int bestBidAskLength = 0;
  private long nextSequence = 1;
  private long nextId = 1;

  private MediaDriver driver;
  private Aeron aeron;
  private ExclusivePublication publication;
  private Subscription subscription;
  private Agent reader;
  private Evaluator evaluator;

  @BeforeEach
  void start() {
    driver =
        MediaDriver.launchEmbedded(
            new MediaDriver.Context()
                .threadingMode(ThreadingMode.SHARED)
                .dirDeleteOnStart(true)
                .dirDeleteOnShutdown(true));
    aeron = Aeron.connect(new Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName()));
    publication = aeron.addExclusivePublication(CHANNEL, STREAM_ID);
    subscription = aeron.addSubscription(CHANNEL, STREAM_ID);
    long deadline = System.nanoTime() + DEADLINE_NANOS;
    while (!subscription.isConnected()) {
      assertThat(System.nanoTime()).as("subscription connected").isLessThan(deadline);
      Thread.onSpinWait();
    }

    MarketDataService service =
        new MarketDataService(
            new MarketDataEventChecker(
                new Deduplicator(instruments.size()),
                new Deduplicator(instruments.size()),
                new SequenceGapDetector(),
                counters),
            RING_SIZE,
            BackpressureStrategy.DROP_OLDEST,
            counters);
    reader = new AeronMarketDataSource(subscription, instruments, service).agent();
    DeployedModel deployed =
        DeployedModel.deploy(
            model, Map.of("trades", instruments.get(0), "quotes", instruments.get(0)), instruments);
    evaluator =
        new Evaluator(
            service.newPoller(),
            service.ringOverflowMechanism(),
            counters,
            instruments.size(),
            List.of(deployed));
    encodeTheFixedFields();
  }

  @AfterEach
  void stop() {
    CloseHelper.closeAll(subscription, publication, aeron, driver);
  }

  @Test
  void allocatesNothingInSteadyState() throws Exception {
    var threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();

    run(); // warm-up, so the JIT has compiled the path
    long before = threads.getCurrentThreadAllocatedBytes();
    run();
    long allocated = threads.getCurrentThreadAllocatedBytes() - before;

    assertThat(model.evaluations).as("the model ran").isGreaterThan(MESSAGES);
    assertThat(model.sum).isNotZero();
    assertThat(counters.missedMessages()).as("no gap: nothing was logged").isZero();
    assertThat(counters.duplicateTrades() + counters.duplicateBestBidAsks()).isZero();
    assertThat(counters.droppedEvents() + counters.skippedEvents())
        .as("the ring never filled")
        .isZero();
    assertThat(counters.failedEvaluations()).isZero();
    assertThat(allocated).isLessThan(TOLERATED_BYTES);
  }

  /**
   * Publishes {@link #MESSAGES} messages in batches, alternating trades and top-of-book updates,
   * and after each batch runs the reader until it has read them all and the evaluator until the
   * ring is empty.
   */
  private void run() throws Exception {
    for (int published = 0; published < MESSAGES; published += BATCH) {
      for (int i = 0; i < BATCH; i += 2) {
        publishTrade();
        publishBestBidAsk();
      }
      int read = 0;
      long deadline = System.nanoTime() + DEADLINE_NANOS;
      while (read < BATCH) {
        read += reader.doWork();
        failAfter(deadline, "the reader did not read the batch in time");
      }
      while (evaluator.doWork() > 0) {
        // Each duty cycle handles a bounded batch: repeat until the ring is empty
      }
    }
  }

  /**
   * Fails once a deadline has passed. A plain check, not an assertion: an assertion object per call
   * would be counted as the path's allocation.
   *
   * @param deadline the {@link System#nanoTime()} it must not pass
   * @param what what did not happen in time
   */
  private static void failAfter(long deadline, String what) {
    if (System.nanoTime() - deadline >= 0) {
      throw new AssertionError(what);
    }
  }

  /** Writes what every message shares once, so publishing only changes what varies. */
  private void encodeTheFixedFields() {
    trade
        .wrapAndApplyHeader(tradeBuffer, 0, header)
        .aggressorSide(Side.BUY)
        .venue((CharSequence) "BINANCE")
        .symbol((CharSequence) "BTCUSDT");
    trade.quantity().mantissa(2);
    tradeLength = MessageHeaderEncoder.ENCODED_LENGTH + trade.encodedLength();
    bestBidAsk
        .wrapAndApplyHeader(bestBidAskBuffer, 0, header)
        .venue((CharSequence) "BINANCE")
        .symbol((CharSequence) "BTCUSDT");
    bestBidAsk.bidQuantity().mantissa(1);
    bestBidAsk.askQuantity().mantissa(1);
    bestBidAskLength = MessageHeaderEncoder.ENCODED_LENGTH + bestBidAsk.encodedLength();
  }

  private void publishTrade() {
    long id = nextId++;
    trade.sequence(nextSequence++).tradeId(id).exchangeTimestamp(id).receiveTimestamp(id);
    trade.price().mantissa(100 + id % 10);
    offer(tradeBuffer, tradeLength);
  }

  private void publishBestBidAsk() {
    long id = nextId++;
    bestBidAsk.sequence(nextSequence++).bookUpdateId(id).exchangeTimestamp(id).receiveTimestamp(id);
    bestBidAsk.bidPrice().mantissa(99 + id % 10);
    bestBidAsk.askPrice().mantissa(101 + id % 10);
    offer(bestBidAskBuffer, bestBidAskLength);
  }

  private void offer(UnsafeBuffer buffer, int length) {
    long deadline = System.nanoTime() + DEADLINE_NANOS;
    while (publication.offer(buffer, 0, length) < 0) {
      failAfter(deadline, "the publication did not take the message in time");
      Thread.onSpinWait();
    }
  }

  /**
   * A model with a sliding window on trades and a tumbling one on top-of-book updates, which reads
   * every event its windows hand it. Allocation-free, like a built-in model must be.
   */
  private static final class SummingModel implements Model {

    private final ModelConfiguration configuration =
        ModelConfiguration.builder()
            .input("trades", TRADE, WindowDefinition.sliding(20))
            .input("quotes", BEST_BID_ASK, WindowDefinition.tumbling(10))
            .build();
    private long evaluations = 0;
    private long sum = 0;

    @Override
    public ModelConfiguration modelConfiguration() {
      return configuration;
    }

    @Override
    public void evaluate(ModelInputs inputs) {
      evaluations++;
      for (Trade trade : inputs.trades("trades").events()) {
        sum += trade.priceMantissa();
      }
      for (BestBidAsk quote : inputs.bestBidAsks("quotes").events()) {
        sum += quote.askPriceMantissa() - quote.bidPriceMantissa();
      }
      sum += inputs.trades("trades").now();
    }
  }
}
