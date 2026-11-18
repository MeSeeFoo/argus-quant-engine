package com.argus.modelengine.application;

import com.argus.log.RateLimitedLog;
import com.argus.modelengine.domain.marketdata.MarketEvent;
import com.argus.modelengine.domain.marketdata.MarketEventType;
import com.argus.modelengine.domain.model.DeployedInput;
import com.argus.modelengine.domain.model.DeployedModel;
import com.argus.modelengine.domain.model.Model;
import com.argus.modelengine.domain.model.MutableModelInputs;
import com.argus.modelengine.domain.window.EventWindow;
import com.argus.modelengine.domain.window.Window;
import com.lmax.disruptor.EventPoller;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import org.agrona.concurrent.Agent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The evaluator agent (ADR 0029): drains the Disruptor ring, appends each event to the windows of
 * its instrument and kind, and runs the model of every window that fires, with all its inputs.
 *
 * <p>The subscriptions are laid out at startup in one table, indexed by kind of event and then by
 * instrument id, so routing an event is two array lookups and a loop over the windows that want it.
 * A new kind of event needs a value of {@link MarketEventType} and its windows, not a new table.
 *
 * <p>Runs on its own thread; the windows and the models are used only here, so they need no locks.
 * Allocation-free in steady state.
 */
public final class Evaluator implements Agent {

  private static final Logger LOG = LoggerFactory.getLogger(Evaluator.class);
  // A broken model may throw on every event: the counters count them, the log samples them
  private static final Duration FAILURE_LOG_INTERVAL = Duration.ofSeconds(10);
  // Shared by every cell that nobody subscribes to: cells are replaced, never written to
  private static final Subscription<?>[] NO_SUBSCRIPTIONS = new Subscription<?>[0];

  /** Events handled per duty cycle at most, so a burst never holds the thread for too long. */
  static final int BATCH_LIMIT = 256;

  private final EventPoller<MarketDataSlot> poller;
  private final RingOverflowMechanism ringOverflowMechanism;
  // [event type index][instrument id]: the windows that take those events
  private final Subscription<?>[][][] subscriptions;
  private final MarketDataCounters counters;
  // Created once: a method reference made at each poll would allocate
  private final EventPoller.Handler<MarketDataSlot> handler = this::onEvent;

  private int handled = 0;

  /**
   * Creates the windows of every deployed model, one per input, and subscribes each to its
   * instrument and kind of event. Startup only.
   *
   * @param poller drains the ring the reader publishes to; created once, gating the ring
   * @param ringOverflowMechanism what the ring does when full, which may ask the evaluator to skip
   *     its backlog
   * @param counters where failed evaluations are counted
   * @param instruments how many instruments the engine handles, which number them from 0
   * @param models every deployed model the engine runs
   * @throws IllegalArgumentException if a model reads an instrument outside that range
   */
  public Evaluator(
      EventPoller<MarketDataSlot> poller,
      RingOverflowMechanism ringOverflowMechanism,
      MarketDataCounters counters,
      int instruments,
      List<DeployedModel> models) {
    this.poller = poller;
    this.ringOverflowMechanism = ringOverflowMechanism;
    this.counters = counters;
    this.subscriptions = subscribeToDeployedModels(models, instruments);
    LOG.info(
        "Evaluating {} deployed models, with {} windows",
        models.size(),
        models.stream().mapToInt(model -> model.inputs().size()).sum());
  }

  /**
   * Skips the backlog if the ring overflowed and the strategy drops the oldest events, logs what
   * the ring lost, then handles up to {@link #BATCH_LIMIT} events.
   *
   * @return the number of events handled
   * @throws Exception if the poller fails
   */
  @Override
  public int doWork() throws Exception {
    handled = 0;
    // Between two polls this thread reads no slot, so it can move its own sequence safely
    ringOverflowMechanism.skipIfRequested(poller.getSequence());
    ringOverflowMechanism.logLosses();
    poller.poll(handler);
    return handled;
  }

  @Override
  public String roleName() {
    return "evaluator";
  }

  /**
   * Handles one slot of the ring: appends its event to every window subscribed to its instrument
   * and kind, and runs the model of each one that fires. Evaluator thread. Allocation-free.
   *
   * @param slot the slot, valid only during this call
   * @param sequence its position in the ring
   * @param endOfBatch whether it is the last published one
   * @return {@code true} to go on with the next slot, {@code false} once the batch limit is reached
   */
  private boolean onEvent(MarketDataSlot slot, long sequence, boolean endOfBatch) {
    MarketEvent event = slot.event();
    for (Subscription<?> subscription : subscriptions[slot.kind().index()][event.instrumentId()]) {
      if (subscription.append(event)) {
        subscription.deployment().inputs().fired(subscription.input());
        run(subscription.deployment());
      }
    }
    handled++;
    return handled < BATCH_LIMIT;
  }

  /**
   * Runs a deployed model with all its inputs. A model that throws is counted and logged, at most
   * once per interval for each deployment, and the evaluator goes on: the event is not handled
   * again, so one failing model cannot stop the others.
   *
   * @param deployment the deployment whose window fired, with the one that fired recorded
   */
  private void run(Deployment deployment) {
    try {
      deployment.model().evaluate(deployment.inputs());
    } catch (RuntimeException failure) {
      counters.countFailedEvaluation();
      deployment.failed(failure);
    }
  }

  /**
   * Builds the table of subscriptions: for every deployed model, a window per input and the inputs
   * the model reads, each window subscribed to the cell of its instrument and kind of event. Cells
   * keep the order the models were deployed in. Startup only.
   *
   * @param deployedModels every deployed model
   * @param instruments how many instruments the engine handles
   * @return the table, indexed by kind of event and then by instrument id
   * @throws IllegalArgumentException if an input reads an instrument the engine does not handle
   */
  private static Subscription<?>[][][] subscribeToDeployedModels(
      List<DeployedModel> deployedModels, int instruments) {
    Subscription<?>[][][] table = emptyTable(instruments);
    for (DeployedModel deployed : deployedModels) {
      List<DeployedInput> deployedInputs = deployed.inputs();
      EventWindow<?>[] windows = windowsOf(deployedInputs, instruments);
      Deployment deployment =
          new Deployment(deployed.model(), new MutableModelInputs(viewsOf(windows)));
      for (int i = 0; i < deployedInputs.size(); i++) {
        int type = deployedInputs.get(i).eventType().index();
        int id = deployedInputs.get(i).instrumentId();
        table[type][id] =
            withSubscription(table[type][id], Subscription.of(windows[i], deployment, i));
      }
    }
    return table;
  }

  /**
   * Creates the table of subscriptions with no subscription in any cell. Startup only.
   *
   * @param instruments how many instruments the engine handles
   * @return the table, indexed by kind of event and then by instrument id
   */
  private static Subscription<?>[][][] emptyTable(int instruments) {
    Subscription<?>[][][] table = new Subscription<?>[MarketEventType.COUNT][instruments][];
    for (Subscription<?>[][] byInstrument : table) {
      Arrays.fill(byInstrument, NO_SUBSCRIPTIONS);
    }
    return table;
  }

  /**
   * Creates the window of each input, in input order. Startup only.
   *
   * @param inputs a deployed model's inputs
   * @param instruments how many instruments the engine handles
   * @return their windows
   * @throws IllegalArgumentException if an input reads an instrument the engine does not handle
   */
  private static EventWindow<?>[] windowsOf(List<DeployedInput> inputs, int instruments) {
    EventWindow<?>[] windows = new EventWindow<?>[inputs.size()];
    for (int i = 0; i < inputs.size(); i++) {
      DeployedInput input = inputs.get(i);
      if (input.instrumentId() >= instruments) {
        throw new IllegalArgumentException(
            "input '" + input.name() + "' reads an instrument the engine does not handle");
      }
      windows[i] = EventWindow.of(input.name(), input.eventType(), input.definition().window());
    }
    return windows;
  }

  /**
   * The read-only view of each window, what the model receives. Startup only.
   *
   * @param windows a deployed model's windows
   * @return their views, in the same order
   */
  private static Window<?>[] viewsOf(EventWindow<?>[] windows) {
    Window<?>[] views = new Window<?>[windows.length];
    for (int i = 0; i < windows.length; i++) {
      views[i] = windows[i].view();
    }
    return views;
  }

  /**
   * A cell with one more subscription at its end. The cell given is left as it is: the copy
   * replaces it in the table. Startup only, where copying a few entries costs nothing.
   *
   * @param cell the subscriptions of one instrument and kind of event
   * @param subscription the subscription to add
   * @return a new cell, with the same subscriptions followed by the new one
   */
  private static Subscription<?>[] withSubscription(
      Subscription<?>[] cell, Subscription<?> subscription) {
    Subscription<?>[] grown = Arrays.copyOf(cell, cell.length + 1);
    grown[cell.length] = subscription;
    return grown;
  }

  /**
   * One deployed model as the evaluator runs it: the model, the inputs it reads, and its failures.
   * Each deployment logs its own failures, so one that keeps throwing never hides another's.
   * Evaluator thread only.
   */
  private static final class Deployment {

    private final Model model;
    private final MutableModelInputs inputs;
    private final RateLimitedLog failureLog = new RateLimitedLog(LOG, FAILURE_LOG_INTERVAL);
    // Built once, so logging a failure builds no message
    private final String failureMessage;
    private long failures = 0;

    /**
     * Creates the deployment of a model with its inputs. Startup only.
     *
     * @param model the model
     * @param inputs its inputs, one per window
     */
    Deployment(Model model, MutableModelInputs inputs) {
      this.model = model;
      this.inputs = inputs;
      // The model's class until deployments have an id (#112)
      this.failureMessage = "Model " + model.getClass().getName() + " threw while evaluating";
    }

    /**
     * The model.
     *
     * @return the model
     */
    Model model() {
      return model;
    }

    /**
     * The inputs the model reads.
     *
     * @return its inputs
     */
    MutableModelInputs inputs() {
      return inputs;
    }

    /**
     * Counts a failed evaluation, and logs it at most once per interval.
     *
     * @param failure what the model threw
     */
    void failed(RuntimeException failure) {
      failures++;
      failureLog.error(failureMessage, failures, failure);
    }
  }

  /**
   * A window of one deployment input, subscribed to the events of its instrument and kind: what the
   * evaluator appends to, and which deployment to run when it fires. Generic, so one table holds
   * every kind of event.
   *
   * @param window the input's window
   * @param deployment the deployment, run when the window fires
   * @param input the window's position among the deployment's inputs
   * @param <E> the kind of event the window takes
   */
  private record Subscription<E extends MarketEvent>(
      EventWindow<E> window, Deployment deployment, int input) {

    /**
     * Creates the subscription of a window whose kind of event is only known at runtime. Startup
     * only.
     *
     * @param window the window
     * @param deployment the deployment
     * @param input the window's position among its inputs
     * @param <E> the kind of event the window takes
     * @return the subscription
     */
    static <E extends MarketEvent> Subscription<E> of(
        EventWindow<E> window, Deployment deployment, int input) {
      return new Subscription<>(window, deployment, input);
    }

    /**
     * Appends an event to the window. Allocation-free.
     *
     * @param event an event of the window's kind, valid only during this call
     * @return {@code true} if the window fires
     */
    @SuppressWarnings("unchecked") // The table is indexed by the window's own kind of event
    boolean append(MarketEvent event) {
      return window.append((E) event) != null;
    }
  }
}
