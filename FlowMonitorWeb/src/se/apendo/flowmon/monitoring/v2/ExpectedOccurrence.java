package se.apendo.flowmon.monitoring.v2;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public final class ExpectedOccurrence {
    public String occurrenceId;
    public String flowId;

    public ScheduleType scheduleType;
    public LocalDate businessDate;

    public ZonedDateTime windowStart;
    public ZonedDateTime windowEnd;
    public ZonedDateTime dueAt;

    public int expected;
    public int actual;

    public String label;
    public boolean exactCount;

    public int missing() {
        int m = expected - actual;
        return m > 0 ? m : 0;
    }
}
