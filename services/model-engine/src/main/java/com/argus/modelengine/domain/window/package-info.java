/**
 * The stores that keep the latest events per instrument and kind, in reused objects, and the
 * windows over them (ADR 0029). Stores and windows move together, so they share this package.
 * Evaluator thread only, allocation-free.
 */
package com.argus.modelengine.domain.window;
