package com.argus.modelengine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point of the model engine. */
@SpringBootApplication
public class ModelEngineApplication {

  /**
   * Starts the engine.
   *
   * @param args Spring Boot's command-line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(ModelEngineApplication.class, args);
  }
}
