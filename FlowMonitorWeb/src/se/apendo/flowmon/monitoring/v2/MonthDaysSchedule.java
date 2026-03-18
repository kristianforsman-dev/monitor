package se.apendo.flowmon.monitoring.v2;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

public final class MonthDaysSchedule extends Schedule {
    public Set<Integer> monthDays = new HashSet<Integer>();
    public int expected;
    public LocalTime dueTime;
    public CarryOverMode carryOverMode = CarryOverMode.SAME_DAY;

    public MonthDaysSchedule() {
        super(ScheduleType.MONTH_DAYS);
    }
}
