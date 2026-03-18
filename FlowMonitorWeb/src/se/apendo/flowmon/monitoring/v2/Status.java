package se.apendo.flowmon.monitoring.v2;

public enum Status {
    INFO,
    WARNING,
    ERROR;

    public int rank() {
        switch (this) {
            case ERROR: return 3;
            case WARNING: return 2;
            default: return 1;
        }
    }
}
