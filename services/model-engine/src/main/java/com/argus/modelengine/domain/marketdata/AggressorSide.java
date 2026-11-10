package com.argus.modelengine.domain.marketdata;

/** The side that took liquidity in a trade. */
public enum AggressorSide {
  /** The buyer crossed the spread. */
  BUY,
  /** The seller crossed the spread. */
  SELL
}
