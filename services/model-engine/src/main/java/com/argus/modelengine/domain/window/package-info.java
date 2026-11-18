/**
 * The windows models declare, by number of events: sliding and tumbling (ADR 0029). Each window
 * keeps its own copies of its events, in objects allocated once, and the read-only {@link
 * com.argus.modelengine.domain.window.Window} view is what a model receives when it fires.
 * Evaluator thread only, allocation-free.
 */
package com.argus.modelengine.domain.window;
