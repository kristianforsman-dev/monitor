package se.apendo.flowmon.monitoring.v2;

import java.util.ArrayList;
import java.util.List;

public class WeekdaysSchedule extends Schedule {
    public List<WeekdayRule> rules = new ArrayList<WeekdayRule>();

    public WeekdaysSchedule() {
        super(ScheduleType.WEEKDAYS);
    }
}
