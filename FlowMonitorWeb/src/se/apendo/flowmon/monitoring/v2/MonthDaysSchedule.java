package se.apendo.flowmon.monitoring.v2;

import java.util.ArrayList;
import java.util.List;

public class MonthDaysSchedule extends Schedule {
    public List<MonthDayRule> rules = new ArrayList<MonthDayRule>();

    public MonthDaysSchedule() {
        super(ScheduleType.MONTH_DAYS);
    }
}
