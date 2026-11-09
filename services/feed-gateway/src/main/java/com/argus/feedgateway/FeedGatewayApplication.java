package com.argus.feedgateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the feed gateway. */
@SpringBootApplication
public class FeedGatewayApplication {

  /**
   * Starts the gateway.
   *
   * @param args Spring Boot's command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(FeedGatewayApplication.class, args);
  }
}
