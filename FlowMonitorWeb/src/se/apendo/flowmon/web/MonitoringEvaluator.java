package se.apendo.flowmon.web;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import se.apendo.flowmon.core.PollerService;

public final class MonitoringEvaluator {

    private static final ZoneId ZONE = ZoneId.of("Europe/Stockholm");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private MonitoringEvaluator() {}

    public static EvalResult eval(
            ZonedDateTime now,
            PollerService poller,
            String flowId,
            MonitoringConfigLoader.Flow f,
            MonitoringConfigLoader.Defaults d) {

        String mode = t(f.mode);
        if ("exactTimes".equalsIgnoreCase(mode)) {
            return evalExactTimes(now, poller, flowId, f, d);
        }
        return evalIntervals(now, poller, flowId, f, d);
    }

    private static EvalResult evalIntervals(
            ZonedDateTime now,
            PollerService poller,
            String flowId,
            MonitoringConfigLoader.Flow f,
            MonitoringConfigLoader.Defaults d) {

        if (f.intervals == null || f.intervals.isEmpty()) return EvalResult.info("", "");

        int warnLead = (f.warningLeadMinutes != null) ? f.warningLeadMinutes.intValue() : d.warningLeadMinutes;
        int grace = (f.errorGraceMinutes != null) ? f.errorGraceMinutes.intValue() : d.errorGraceMinutes;

        StringBuilder details = new StringBuilder();
        EvalResult worst = EvalResult.info("", "");
        String worstMsg = "";

        int i = 0;
        for (MonitoringConfigLoader.Interval in : f.intervals) {
            if (in == null) continue;

            LocalTime start = in.start;
            LocalTime end = in.end;
            int expected = in.expected;
            if (start == null || end == null || expected <= 0) continue;

            ZonedDateTime endZ = now.toLocalDate().atTime(end).atZone(ZONE);
            ZonedDateTime warnAt = endZ.minusMinutes(warnLead);
            ZonedDateTime errAt = endZ.plusMinutes(grace);

            long actual = (poller == null) ? 0L : poller.getFinishedBetween(flowId, start, end);

            if (i > 0) details.append(", ");
            details.append(start.format(TIME)).append("-").append(end.format(TIME))
                   .append(" ").append(actual).append("/").append(expected);

            EvalResult cur = EvalResult.info("", "");

            double tol = (in.tolerancePct != null) ? in.tolerancePct.doubleValue() : 0.0;
            long minOk = (long)Math.floor(expected * (1.0 - tol));

            if (now.isAfter(errAt)) {
                if (actual < minOk) {
                    long missing = (minOk - actual);
                    cur = EvalResult.error("Intervall " + start.format(TIME) + "-" + end.format(TIME) + ": ligger efter. Saknar " + missing, "");
                }
            } else if (!now.isBefore(warnAt)) {
                double warnAtPct = (in.warnAtPct != null) ? in.warnAtPct.doubleValue() : 1.0;
                long warnMin = (long)Math.floor(expected * warnAtPct);
                if (actual < warnMin) {
                    long missing = (warnMin - actual);
                    cur = EvalResult.warning("Intervall " + start.format(TIME) + "-" + end.format(TIME) + ": ligger efter. Saknar " + missing, "");
                }
            }

            if (cur.rank > worst.rank) {
                worst = cur;
                worstMsg = cur.message;
            }

            i++;
        }

        worst.details = details.toString();
        worst.message = worstMsg;
        return worst;
    }

    private static EvalResult evalExactTimes(
            ZonedDateTime now,
            PollerService poller,
            String flowId,
            MonitoringConfigLoader.Flow f,
            MonitoringConfigLoader.Defaults d) {

        if (f.times == null || f.times.isEmpty()) return EvalResult.info("", "");

        int warnLead = (f.warningLeadMinutes != null) ? f.warningLeadMinutes.intValue() : d.warningLeadMinutes;
        int grace = (f.errorGraceMinutes != null) ? f.errorGraceMinutes.intValue() : d.errorGraceMinutes;

        StringBuilder details = new StringBuilder();
        EvalResult worst = EvalResult.info("", "");
        String worstMsg = "";

        int i = 0;
        for (MonitoringConfigLoader.ExactTime et : f.times) {
            if (et == null || et.time == null || et.expected <= 0) continue;

            LocalTime t = et.time;
            int expected = et.expected;

            ZonedDateTime tZ = now.toLocalDate().atTime(t).atZone(ZONE);
            ZonedDateTime warnAt = tZ.minusMinutes(warnLead);
            ZonedDateTime errAt = tZ.plusMinutes(grace);

            long actual = (poller == null) ? 0L : poller.getFinishedBetween(flowId, LocalTime.MIDNIGHT, t);

            if (i > 0) details.append(", ");
            details.append(t.format(TIME)).append(" ").append(actual).append("/").append(expected);

            EvalResult cur = EvalResult.info("", "");

            if (now.isAfter(errAt)) {
                if (actual < expected) {
                    long missing = expected - actual;
                    cur = EvalResult.error("Tid " + t.format(TIME) + ": saknar " + missing, "");
                }
            } else if (!now.isBefore(warnAt)) {
                if (actual < expected) {
                    long missing = expected - actual;
                    cur = EvalResult.warning("Tid " + t.format(TIME) + ": saknar " + missing, "");
                }
            }

            if (cur.rank > worst.rank) {
                worst = cur;
                worstMsg = cur.message;
            }

            i++;
        }

        worst.details = details.toString();
        worst.message = worstMsg;
        return worst;
    }

    private static String t(String s) {
        return (s == null) ? "" : s.trim();
    }

    public static final class EvalResult {
        public String status;
        public String message;
        public String details;
        public int rank;

        public static EvalResult info(String msg, String details) {
            EvalResult r = new EvalResult();
            r.status = "INFO";
            r.message = (msg == null ? "" : msg);
            r.details = (details == null ? "" : details);
            r.rank = 1;
            return r;
        }

        public static EvalResult warning(String msg, String details) {
            EvalResult r = new EvalResult();
            r.status = "WARNING";
            r.message = (msg == null ? "" : msg);
            r.details = (details == null ? "" : details);
            r.rank = 2;
            return r;
        }

        public static EvalResult error(String msg, String details) {
            EvalResult r = new EvalResult();
            r.status = "ERROR";
            r.message = (msg == null ? "" : msg);
            r.details = (details == null ? "" : details);
            r.rank = 3;
            return r;
        }
    }
}
