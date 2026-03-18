package se.apendo.flowmon.monitoring.v2;

import java.time.LocalDate;
import java.time.LocalTime;

public class DateRule {
    public LocalDate date;
    public int expected;
    public LocalTime dueTime;
    public CarryOverMode carryOverMode;
}
