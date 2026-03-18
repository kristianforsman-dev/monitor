package se.apendo.flowmon.monitoring.v2;

import java.time.LocalTime;

public class WeekdayRule {
    public int weekday;
    public int expected;
    public LocalTime dueTime;
    public CarryOverMode carryOverMode;
}
