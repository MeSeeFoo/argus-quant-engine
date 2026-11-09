package com.argus.feedgateway.domain;

/** The side that crossed the spread in a trade. */
public enum AggressorSide {
  /** The buyer crossed the spread: the seller's order was resting in the book. */
  BUY,
  /** The seller crossed the spread: the buyer's order was resting in the book. */
  SELL
}
