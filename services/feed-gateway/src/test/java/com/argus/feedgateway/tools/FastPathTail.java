package com.argus.feedgateway.tools;

import com.argus.contracts.sbe.BestBidAskDecoder;
import com.argus.contracts.sbe.HeartbeatDecoder;
import com.argus.contracts.sbe.MessageHeaderDecoder;
import com.argus.contracts.sbe.TradeDecoder;
import io.aeron.Aeron;
import io.aeron.Subscription;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeron.logbuffer.FragmentHandler;
import org.agrona.concurrent.BackoffIdleStrategy;
import org.agrona.concurrent.IdleStrategy;

/**
 * Prints what a running gateway publishes, for checking the fast path by hand. It registers with
 * the gateway's control endpoint over UDP, like the model engine will. Not a test: run it as shown
 * in the module README, and stop it with Ctrl+C.
 */
public final class FastPathTail {

  private FastPathTail() {}

  /**
   * Tails the stream until interrupted.
   *
   * @param args the gateway's control endpoint (default {@code localhost:40456}) and stream id
   *     (default {@code 1001})
   */
  public static void main(String[] args) {
    String control = args.length > 0 ? args[0] : "localhost:40456";
    int streamId = args.length > 1 ? Integer.parseInt(args[1]) : 1001;
    MessageHeaderDecoder header = new MessageHeaderDecoder();
    TradeDecoder trade = new TradeDecoder();
    BestBidAskDecoder bestBidAsk = new BestBidAskDecoder();
    HeartbeatDecoder heartbeat = new HeartbeatDecoder();
    FragmentHandler print =
        (buffer, offset, length, ignored) -> {
          header.wrap(buffer, offset);
          switch (header.templateId()) {
            case TradeDecoder.TEMPLATE_ID ->
                System.out.println(trade.wrapAndApplyHeader(buffer, offset, header));
            case BestBidAskDecoder.TEMPLATE_ID ->
                System.out.println(bestBidAsk.wrapAndApplyHeader(buffer, offset, header));
            case HeartbeatDecoder.TEMPLATE_ID ->
                System.out.println(heartbeat.wrapAndApplyHeader(buffer, offset, header));
            default -> System.out.println("Unknown template " + header.templateId());
          }
        };

    try (MediaDriver driver =
            MediaDriver.launchEmbedded(
                new MediaDriver.Context()
                    .threadingMode(ThreadingMode.SHARED)
                    .dirDeleteOnStart(true)
                    .dirDeleteOnShutdown(true));
        Aeron aeron =
            Aeron.connect(new Aeron.Context().aeronDirectoryName(driver.aeronDirectoryName()));
        Subscription subscription =
            aeron.addSubscription(
                "aeron:udp?endpoint=localhost:0|control=" + control + "|control-mode=dynamic",
                streamId)) {
      System.out.println("Listening to " + control + ", stream " + streamId);
      IdleStrategy idle = new BackoffIdleStrategy();
      while (!Thread.currentThread().isInterrupted()) {
        idle.idle(subscription.poll(print, 10));
      }
    }
  }
}
