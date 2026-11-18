# argus-rate-limited-log

Logs a problem that repeats at most once per interval, with how many there were so far: a transport that is down, a model that keeps throwing, a ring that keeps overflowing. The caller counts every occurrence in its metrics; the log only samples them, so it never floods.

It is shared by the feed gateway and the model engine. It holds this one class on purpose: it is not a place for general helpers.

## Using it

Add the dependency, whose version is managed by the parent POM:

```xml
<dependency>
    <groupId>com.argus</groupId>
    <artifactId>argus-rate-limited-log</artifactId>
</dependency>
```

Create one per kind of problem, with the caller's own logger:

```java
private static final Logger LOG = LoggerFactory.getLogger(KafkaSenderAgent.class);
private final RateLimitedLog failureLog = new RateLimitedLog(LOG, Duration.ofSeconds(10));

counters.countFailedSending();
failureLog.error("Could not hand an event to Kafka", counters.failedSending(), e);
```

- **Levels.** `error` logs with the exception, `warn` without one. Both share the interval.
- **Hot path.** A call that is not logged allocates nothing, so it can be made on every occurrence; a call that is logged allocates its line, at most once per interval.
- **Threads.** Safe from any thread: only one wins each interval.
- **Clock.** The interval is measured with the monotonic clock; it times the log, never events.
