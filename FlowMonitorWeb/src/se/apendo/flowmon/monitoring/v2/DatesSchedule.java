package se.apendo.flowmon.monitoring.v2;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

public final class DatesSchedule extends Schedule {
    public Set<LocalDate> dates = new HashSet<LocalDate>();
    public int expected;
    public LocalTime dueTime;
    public CarryOverMode carryOverMode = CarryOverMode.SAME_DAY;

    public DatesSchedule() {
        super(ScheduleType.DATES);
    }
}
