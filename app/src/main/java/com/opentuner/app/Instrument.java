package com.opentuner.app;

public enum Instrument {
    GUITAR("Guitar", 6),
    BASS("Bass", 4);

    public final String label;
    public final int stringCount;

    Instrument(String label, int stringCount) {
        this.label = label;
        this.stringCount = stringCount;
    }
}
