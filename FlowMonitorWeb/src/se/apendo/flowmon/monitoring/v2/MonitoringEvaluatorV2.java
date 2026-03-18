package se.apendo.flowmon.monitoring.v2;

import java.time.ZonedDateTime;
import java.util.List;

public final class MonitoringEvaluatorV2 {

    private final OccurrenceGenerator occurrenceGenerator;

    public MonitoringEvaluatorV2() {
        this(new DefaultOccurrenceGenerator());
    }

    public MonitoringEvaluatorV2(OccurrenceGenerator occurrenceGenerator) {
        this.occurrenceGenerator = occurrenceGenerator;
    }

    public EvaluationResult evaluate(
        ZonedDateTime now,
        FlowMonitoringRule rule,
        MonitoringDefaults defaults,
        FlowEventCounter counter
    ) throws Exception {
        EvaluationResult out = new EvaluationResult();
        if (rule == null || !rule.enabled || rule.schedule == null) return out;

        List<ExpectedOccurrence> occurrences = occurrenceGenerator.generate(now, rule, defaults);

        for (ExpectedOccurrence occ : occurrences) {
            ZonedDateTime countTo = now.isBefore(occ.windowEnd) ? now : occ.windowEnd;
            if (countTo.isBefore(occ.windowStart)) {
                occ.actual = 0;
            } else {
                occ.actual = counter.countFinishedBetween(occ.flowId, occ.windowStart, countTo);
            }

            OccurrenceResult r = evaluateOccurrence(now, occ, rule, defaults);
            out.occurrences.add(r);

            if (r.status.rank() > out.rank) {
                out.rank = r.status.rank();
                out.status = r.status;
                out.message = r.message;
            }

            if (out.nextDueAt == null || occ.dueAt.isBefore(out.nextDueAt)) {
                out.nextDueAt = occ.dueAt;
            }
        }

        ZonedDateTime last = counter.getLastFinishedCompleted(rule.flowId);
        if (last != null) {
            out.details = "Senast " + String.format("%02d:%02d:%02d",
                Integer.valueOf(last.getHour()),
                Integer.valueOf(last.getMinute()),
                Integer.valueOf(last.getSecond()));
        }

        return out;
    }

    private OccurrenceResult evaluateOccurrence(
        ZonedDateTime now,
        ExpectedOccurrence occ,
        FlowMonitoringRule rule,
        MonitoringDefaults defaults
    ) {
        switch (occ.scheduleType) {
            case INTERVAL:
                return evalInterval(now, occ, rule);
            case EXACT_TIMES:
                return evalExact(now, occ, rule, defaults);
            case WEEKDAYS:
            case MONTH_DAYS:
            case DATES:
                return evalDateLike(now, occ, rule, defaults);
            default:
                return base(occ);
        }
    }

    private OccurrenceResult evalInterval(
        ZonedDateTime now,
        ExpectedOccurrence occ,
        FlowMonitoringRule rule
    ) {
        OccurrenceResult r = base(occ);

        IntervalRule cfg = findIntervalRule(rule, occ.label);
        if (cfg == null) return r;

        int actual = occ.actual;
        int expected = occ.expected;

        if (now.isBefore(occ.windowStart)) {
            r.status = Status.INFO;
            r.message = "";
            return r;
        }

        if (!now.isAfter(occ.windowEnd)) {
            int warnMin = (int) Math.floor(expected * cfg.resolvedWarnAtPct());
            if (actual < warnMin) {
                r.status = Status.WARNING;
                r.message = "Intervall " + occ.label + ": ligger efter";
            } else {
                r.status = Status.INFO;
                r.message = "";
            }
            r.missing = Math.max(0, warnMin - actual);
            return r;
        }

        int minOk = (int) Math.floor(expected * (1.0d - cfg.resolvedTolerancePct()));
        if (actual < minOk) {
            r.status = Status.ERROR;
            r.missing = minOk - actual;
            r.message = "Intervall " + occ.label + ": saknar " + r.missing;
        } else {
            r.status = Status.INFO;
            r.message = "";
            r.missing = 0;
        }

        return r;
    }

    private OccurrenceResult evalExact(
        ZonedDateTime now,
        ExpectedOccurrence occ,
        FlowMonitoringRule rule,
        MonitoringDefaults defaults
    ) {
        OccurrenceResult r = base(occ);

        int warningLead = rule.resolveWarningLeadMinutes(defaults);
        ZonedDateTime warnAt = occ.dueAt.minusMinutes(warningLead);

        if (now.isBefore(warnAt)) {
            r.status = Status.INFO;
            r.message = "";
            return r;
        }

        if (now.isBefore(occ.dueAt)) {
            if (occ.actual < occ.expected) {
                r.status = Status.WARNING;
                r.missing = occ.expected - occ.actual;
                r.message = "Tid " + occ.label + ": väntar på " + r.missing;
            } else {
                r.status = Status.INFO;
                r.message = "";
            }
            return r;
        }

        if (occ.actual < occ.expected) {
            r.status = Status.ERROR;
            r.missing = occ.expected - occ.actual;
            r.message = "Tid " + occ.label + ": saknar " + r.missing;
        } else {
            r.status = Status.INFO;
            r.message = "";
        }

        return r;
    }

    private OccurrenceResult evalDateLike(
        ZonedDateTime now,
        ExpectedOccurrence occ,
        FlowMonitoringRule rule,
        MonitoringDefaults defaults
    ) {
        OccurrenceResult r = base(occ);

        int warningLead = rule.resolveWarningLeadMinutes(defaults);
        ZonedDateTime warnAt = occ.dueAt.minusMinutes(warningLead);

        if (now.isBefore(warnAt)) {
            r.status = Status.INFO;
            r.message = "";
            return r;
        }

        if (now.isBefore(occ.dueAt)) {
            if (occ.actual < occ.expected) {
                r.status = Status.WARNING;
                r.missing = occ.expected - occ.actual;
                r.message = "Förväntad leverans " + occ.businessDate + ": väntar på " + r.missing;
            } else {
                r.status = Status.INFO;
                r.message = "";
            }
            return r;
        }

        if (occ.actual < occ.expected) {
            r.status = Status.ERROR;
            r.missing = occ.expected - occ.actual;
            r.message = "Förväntad leverans " + occ.businessDate + ": saknar " + r.missing;
        } else {
            r.status = Status.INFO;
            r.message = "";
        }

        return r;
    }

    private OccurrenceResult base(ExpectedOccurrence occ) {
        OccurrenceResult r = new OccurrenceResult();
        r.occurrenceId = occ.occurrenceId;
        r.label = occ.label;
        r.expected = occ.expected;
        r.actual = occ.actual;
        r.windowStart = occ.windowStart;
        r.windowEnd = occ.windowEnd;
        r.dueAt = occ.dueAt;
        r.missing = Math.max(0, occ.expected - occ.actual);
        r.status = Status.INFO;
        r.message = "";
        return r;
    }

    private IntervalRule findIntervalRule(FlowMonitoringRule rule, String label) {
        if (!(rule.schedule instanceof IntervalSchedule)) return null;
        IntervalSchedule s = (IntervalSchedule) rule.schedule;
        for (IntervalRule r : s.intervals) {
            String x = String.valueOf(r.start) + "-" + String.valueOf(r.end);
            if (x.equals(label)) return r;
        }
        return null;
    }
}
