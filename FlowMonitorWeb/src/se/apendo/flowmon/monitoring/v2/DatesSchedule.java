package se.apendo.flowmon.monitoring.v2;

import java.util.ArrayList;
import java.util.List;

public class DatesSchedule extends Schedule {
    public List<DateRule> rules = new ArrayList<DateRule>();

    public DatesSchedule() {
        super(ScheduleType.DATES);
    }
}
