package se.apendo.flowmon.monitoring.v2;

import java.time.ZonedDateTime;

public final class OccurrenceResult {
    public String occurrenceId;
    public String label;

    public Status status = Status.INFO;

    public int expected;
    public int actual;
    public int missing;

    public ZonedDateTime windowStart;
    public ZonedDateTime windowEnd;
    public ZonedDateTime dueAt;

    public String message;
}
