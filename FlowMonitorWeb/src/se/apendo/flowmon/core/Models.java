package se.apendo.flowmon.core;

import java.util.List;
import java.util.Map;

public final class Models {

    private Models() {}

    public enum Status { OK, WARN, ALARM }

    public static final class LiveRow {
        public final String sender;
        public final String receiver;
        public final String msgType;
        public final String state;
        public final long count;

        public LiveRow(String sender, String receiver, String msgType, String state, long count) {
            this.sender = sender;
            this.receiver = receiver;
            this.msgType = msgType;
            this.state = state;
            this.count = count;
        }
    }

    public static final class FailedRow {
        public final String piid;
        public final String sender;
        public final String receiver;
        public final String msgType;
        public final String startedIso;
        public final String lastChangedIso;

        public FailedRow(String piid, String sender, String receiver, String msgType, String startedIso, String lastChangedIso) {
            this.piid = piid;
            this.sender = sender;
            this.receiver = receiver;
            this.msgType = msgType;
            this.startedIso = startedIso;
            this.lastChangedIso = lastChangedIso;
        }
    }

    public static final class CheckRow {
        public final String flowId;
        public final Status status;
        public final String detail;
        public final long actual;
        public final long expected;

        public final String incidentId;
        public final String problemType;
        public final String periodKey;

        public CheckRow(String flowId, Status status, String detail, long actual, long expected,
                        String incidentId, String problemType, String periodKey) {
            this.flowId = flowId;
            this.status = status;
            this.detail = detail;
            this.actual = actual;
            this.expected = expected;
            this.incidentId = incidentId;
            this.problemType = problemType;
            this.periodKey = periodKey;
        }
    }

    public static final class AckInfo {
        public final String incidentId;
        public final String ackedBy;
        public final String ackedAtIso;
        public final String expiresAtIso;

        public AckInfo(String incidentId, String ackedBy, String ackedAtIso, String expiresAtIso) {
            this.incidentId = incidentId;
            this.ackedBy = ackedBy;
            this.ackedAtIso = ackedAtIso;
            this.expiresAtIso = expiresAtIso;
        }
    }

    public static final class Snapshot {
        public final long version;
        public final String updatedAtIso;

        public final List<LiveRow> liveRunning;
        public final List<FailedRow> failed;

        public final List<CheckRow> interval;
        public final List<CheckRow> exactTimes;
        public final List<CheckRow> weekdays;
        public final List<CheckRow> monthdays;
        public final List<CheckRow> dates;

        public final int flowCount;

        public Snapshot(long version, String updatedAtIso,
                        List<LiveRow> liveRunning,
                        List<FailedRow> failed,
                        List<CheckRow> interval,
                        List<CheckRow> exactTimes,
                        List<CheckRow> weekdays,
                        List<CheckRow> monthdays,
                        List<CheckRow> dates,
                        int flowCount) {
            this.version = version;
            this.updatedAtIso = updatedAtIso;
            this.liveRunning = liveRunning;
            this.failed = failed;
            this.interval = interval;
            this.exactTimes = exactTimes;
            this.weekdays = weekdays;
            this.monthdays = monthdays;
            this.dates = dates;
            this.flowCount = flowCount;
        }
    }

    public static final class FlowConfig {
        public String id;
        public String sender;
        public String receiver;
        public String msgType;
        public Map<String, Object> expectations;
    }
}
