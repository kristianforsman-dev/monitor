package se.apendo.flowmon.monitoring.v2;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

public final class WeekdaysSchedule extends Schedule {
    public Set<DayOfWeek> weekdays = new HashSet<DayOfWeek>();
    public int expected;
    public LocalTime dueTime;
    public CarryOverMode carryOverMode = CarryOverMode.SAME_DAY;

    public WeekdaysSchedule() {
        super(ScheduleType.WEEKDAYS);
    }
}
