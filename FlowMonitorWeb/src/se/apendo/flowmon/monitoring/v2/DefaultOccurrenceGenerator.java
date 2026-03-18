package se.apendo.flowmon.monitoring.v2;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public final class DefaultOccurrenceGenerator implements OccurrenceGenerator {

    @Override
    public List<ExpectedOccurrence> generate(
        ZonedDateTime now,
        FlowMonitoringRule rule,
        MonitoringDefaults defaults
    ) {
        List<ExpectedOccurrence> out = new ArrayList<ExpectedOccurrence>();
        if (rule == null || !rule.enabled || rule.schedule == null) return out;

        switch (rule.schedule.type) {
            case INTERVAL:
                generateIntervals(out, now, rule, (IntervalSchedule) rule.schedule, defaults);
                break;
            case EXACT_TIMES:
                generateExactTimes(out, now, rule, (ExactTimesSchedule) rule.schedule, defaults);
                break;
            case WEEKDAYS:
                generateWeekdays(out, now, rule, (WeekdaysSchedule) rule.schedule, defaults);
                break;
            case MONTH_DAYS:
                generateMonthDays(out, now, rule, (MonthDaysSchedule) rule.schedule, defaults);
                break;
            case DATES:
                generateDates(out, now, rule, (DatesSchedule) rule.schedule, defaults);
                break;
            default:
                break;
        }

        return out;
    }

    private void generateIntervals(
        List<ExpectedOccurrence> out,
        ZonedDateTime now,
        FlowMonitoringRule rule,
        IntervalSchedule schedule,
        MonitoringDefaults defaults
    ) {
        LocalDate d = now.toLocalDate();
        int grace = rule.resolveErrorGraceMinutes(defaults);

        for (IntervalRule r : schedule.intervals) {
            if (r == null || r.start == null || r.end == null || r.expected <= 0) continue;

            ExpectedOccurrence occ = new ExpectedOccurrence();
            occ.flowId = rule.flowId;
            occ.scheduleType = ScheduleType.INTERVAL;
            occ.businessDate = d;
            occ.label = r.start + "-" + r.end;
            occ.exactCount = false;
            occ.expected = r.expected;

            occ.windowStart = ZonedDateTime.of(LocalDateTime.of(d, r.start), now.getZone());
            occ.windowEnd = ZonedDateTime.of(LocalDateTime.of(d, r.end), now.getZone());
            occ.dueAt = occ.windowEnd.plusMinutes(grace);
            occ.occurrenceId = rule.flowId + "|" + d + "|" + occ.label;

            out.add(occ);
        }
    }

    private void generateExactTimes(
        List<ExpectedOccurrence> out,
        ZonedDateTime now,
        FlowMonitoringRule rule,
        ExactTimesSchedule schedule,
        MonitoringDefaults defaults
    ) {
        LocalDate d = now.toLocalDate();
        int grace = rule.resolveErrorGraceMinutes(defaults);

        for (ExactTimeRule r : schedule.times) {
            if (r == null || r.time == null || r.expected <= 0) continue;

            ExpectedOccurrence occ = new ExpectedOccurrence();
            occ.flowId = rule.flowId;
            occ.scheduleType = ScheduleType.EXACT_TIMES;
            occ.businessDate = d;
            occ.label = r.time.toString();
            occ.exactCount = true;
            occ.expected = r.expected;

            occ.windowStart = ZonedDateTime.of(LocalDateTime.of(d, LocalTime.MIDNIGHT), now.getZone());
            occ.windowEnd = ZonedDateTime.of(LocalDateTime.of(d, r.time), now.getZone());
            occ.dueAt = occ.windowEnd.plusMinutes(grace);
            occ.occurrenceId = rule.flowId + "|" + d + "|" + r.time;

            out.add(occ);
        }
    }

    private void generateWeekdays(
        List<ExpectedOccurrence> out,
        ZonedDateTime now,
        FlowMonitoringRule rule,
        WeekdaysSchedule schedule,
        MonitoringDefaults defaults
    ) {
        LocalDate d = now.toLocalDate();
        DayOfWeek dow = d.getDayOfWeek();
        if (!schedule.weekdays.contains(dow)) return;

        LocalTime dueTime = schedule.dueTime != null ? schedule.dueTime : defaults.businessEnd;
        int grace = rule.resolveErrorGraceMinutes(defaults);

        ExpectedOccurrence occ = new ExpectedOccurrence();
        occ.flowId = rule.flowId;
        occ.scheduleType = ScheduleType.WEEKDAYS;
        occ.businessDate = d;
        occ.label = dow.toString();
        occ.exactCount = true;
        occ.expected = schedule.expected;

        occ.windowStart = ZonedDateTime.of(LocalDateTime.of(d, LocalTime.MIDNIGHT), now.getZone());
        occ.windowEnd = ZonedDateTime.of(LocalDateTime.of(d, dueTime), now.getZone());
        occ.dueAt = occ.windowEnd.plusMinutes(grace);
        occ.occurrenceId = rule.flowId + "|" + d + "|WEEKDAY";

        out.add(occ);
    }

    private void generateMonthDays(
        List<ExpectedOccurrence> out,
        ZonedDateTime now,
        FlowMonitoringRule rule,
        MonthDaysSchedule schedule,
        MonitoringDefaults defaults
    ) {
        LocalDate d = now.toLocalDate();
        if (!schedule.monthDays.contains(Integer.valueOf(d.getDayOfMonth()))) return;

        LocalTime dueTime = schedule.dueTime != null ? schedule.dueTime : defaults.businessEnd;
        int grace = rule.resolveErrorGraceMinutes(defaults);

        ExpectedOccurrence occ = new ExpectedOccurrence();
        occ.flowId = rule.flowId;
        occ.scheduleType = ScheduleType.MONTH_DAYS;
        occ.businessDate = d;
        occ.label = String.valueOf(d.getDayOfMonth());
        occ.exactCount = true;
        occ.expected = schedule.expected;

        occ.windowStart = ZonedDateTime.of(LocalDateTime.of(d, LocalTime.MIDNIGHT), now.getZone());
        occ.windowEnd = ZonedDateTime.of(LocalDateTime.of(d, dueTime), now.getZone());
        occ.dueAt = occ.windowEnd.plusMinutes(grace);
        occ.occurrenceId = rule.flowId + "|" + d + "|MONTHDAY";

        out.add(occ);
    }

    private void generateDates(
        List<ExpectedOccurrence> out,
        ZonedDateTime now,
        FlowMonitoringRule rule,
        DatesSchedule schedule,
        MonitoringDefaults defaults
    ) {
        LocalDate d = now.toLocalDate();
        if (!schedule.dates.contains(d)) return;

        LocalTime dueTime = schedule.dueTime != null ? schedule.dueTime : defaults.businessEnd;
        int grace = rule.resolveErrorGraceMinutes(defaults);

        ExpectedOccurrence occ = new ExpectedOccurrence();
        occ.flowId = rule.flowId;
        occ.scheduleType = ScheduleType.DATES;
        occ.businessDate = d;
        occ.label = d.toString();
        occ.exactCount = true;
        occ.expected = schedule.expected;

        occ.windowStart = ZonedDateTime.of(LocalDateTime.of(d, LocalTime.MIDNIGHT), now.getZone());
        occ.windowEnd = ZonedDateTime.of(LocalDateTime.of(d, dueTime), now.getZone());
        occ.dueAt = occ.windowEnd.plusMinutes(grace);
        occ.occurrenceId = rule.flowId + "|" + d + "|DATE";

        out.add(occ);
    }
}
