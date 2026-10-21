package com.argus.feedgateway.adapter.in.binance;

import static org.assertj.core.api.Assertions.assertThat;

import com.argus.feedgateway.application.port.in.IngestMarketData;
import com.argus.feedgateway.domain.BestBidAsk;
import com.argus.feedgateway.domain.Instrument;
import com.argus.feedgateway.domain.Instruments;
import com.argus.feedgateway.domain.Trade;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class BinanceConfigurationTest {

  private final ApplicationContextRunner runner =
      new ApplicationContextRunner()
          .withUserConfiguration(BinanceConfiguration.class)
          .withPropertyValues(
              "argus.feed-gateway.binance.uri=ws://localhost:1",
              "argus.feed-gateway.binance.api-key=test-key")
          .withBean(IngestMarketData.class, NoIngest::new);

  @Test
  void rejectsInstrumentsOfAnotherVenue() {
    runner
        .withBean(
            Instruments.class,
            () ->
                new Instruments(
                    List.of(
                        new Instrument("BINANCE", "BTCUSDT"),
                        new Instrument("COINBASE", "BTCUSD"))))
        .run(
            context ->
                assertThat(context)
                    .hasFailed()
                    .getFailure()
                    .rootCause()
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("only reads BINANCE instruments")
                    .hasMessageContaining("COINBASE:BTCUSD"));
  }

  private static final class NoIngest implements IngestMarketData {

    @Override
    public void onTrade(Trade trade) {}

    @Override
    public void onBestBidAsk(BestBidAsk bestBidAsk) {}
  }
}
