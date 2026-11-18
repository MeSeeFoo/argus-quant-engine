package com.argus.modelengine.application;

import com.argus.modelengine.domain.marketdata.BestBidAsk;
import com.argus.modelengine.domain.marketdata.MarketEvent;
import com.argus.modelengine.domain.marketdata.MarketEventType;
import com.argus.modelengine.domain.marketdata.MutableBestBidAsk;
import com.argus.modelengine.domain.marketdata.MutableTrade;
import com.argus.modelengine.domain.marketdata.Trade;

/**
 * One slot of the Disruptor ring between the reader and the evaluator (ADR 0029). The ring creates
 * every slot once, at startup, and only ever overwrites it, so a slot has room for both kinds of
 * event: a trade and a top-of-book update. {@link #kind} says which one the slot holds now.
 *
 * <pre>{@code
 * MarketEvent event = slot.event();          // or slot.trade() when slot.kind() is TRADE
 * windowsOf(slot.kind(), event.instrumentId());
 * }</pre>
 *
 * <p>Threading: the reader thread fills it in a claimed slot, and the evaluator thread reads it
 * after the slot is published; the Disruptor's sequences make the writes visible. Valid only until
 * the evaluator moves past it, when the reader may claim the slot again. Allocation-free.
 */
public final class MarketDataSlot {

  private final MutableTrade trade = new MutableTrade();
  private final MutableBestBidAsk bestBidAsk = new MutableBestBidAsk();
  private MarketEventType kind = MarketEventType.TRADE;

  /**
   * Makes this slot hold a trade, copying every field. Reader thread.
   *
   * @param source the trade, valid only during this call
   */
  void setTrade(Trade source) {
    trade.copyFrom(source);
    kind = MarketEventType.TRADE;
  }

  /**
   * Makes this slot hold a top-of-book update, copying every field. Reader thread.
   *
   * @param source the update, valid only during this call
   */
  void setBestBidAsk(BestBidAsk source) {
    bestBidAsk.copyFrom(source);
    kind = MarketEventType.BEST_BID_ASK;
  }

  /**
   * Which kind of event the slot holds, and so which of {@link #trade} and {@link #bestBidAsk} to
   * read.
   *
   * @return a trade or a top-of-book update
   */
  public MarketEventType kind() {
    return kind;
  }

  /**
   * The event the slot holds now, whatever its kind: the trade or the top-of-book update, as {@link
   * #kind} says. Allocation-free.
   *
   * @return the slot's current event, valid until the evaluator moves past the slot
   */
  public MarketEvent event() {
    return switch (kind) {
      case TRADE -> trade;
      case BEST_BID_ASK -> bestBidAsk;
    };
  }

  /**
   * The trade, meaningful only when {@link #kind} is {@code TRADE}.
   *
   * @return the slot's trade, valid until the evaluator moves past the slot
   */
  public Trade trade() {
    return trade;
  }

  /**
   * The top-of-book update, meaningful only when {@link #kind} is {@code BEST_BID_ASK}.
   *
   * @return the slot's update, valid until the evaluator moves past the slot
   */
  public BestBidAsk bestBidAsk() {
    return bestBidAsk;
  }
}
