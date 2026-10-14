package com.opentuner.app;

/** A tuning: one MIDI note per string, ordered from the lowest string to the highest. */
public final class Tuning {
    public final String id;
    public final String name;
    public final Instrument instrument;
    public final int[] notes;
    public final boolean builtIn;
    /** Show accidentals as flats (e.g. half-step down tunings). */
    public final boolean flats;

    public Tuning(String id, String name, Instrument instrument, int[] notes, boolean builtIn, boolean flats) {
        this.id = id;
        this.name = name;
        this.instrument = instrument;
        this.notes = notes;
        this.builtIn = builtIn;
        this.flats = flats;
    }

    public String noteName(int stringIndex) {
        return Notes.name(notes[stringIndex], flats);
    }

    public String pitchClassName(int stringIndex) {
        return Notes.pitchClass(notes[stringIndex], flats);
    }

    /** Compact description such as "E2 A2 D3 G3 B3 E4". */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < notes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(noteName(i));
        }
        return sb.toString();
    }
}
