package se.apendo.flowmon.monitoring.v2;

import java.time.LocalTime;

public class MonthDayRule {
    public int day;
    public int expected;
    public LocalTime dueTime;
    public CarryOverMode carryOverMode;
}
