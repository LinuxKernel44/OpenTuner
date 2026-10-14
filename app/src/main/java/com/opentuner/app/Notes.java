package com.opentuner.app;

/** Note math helpers. MIDI numbers: A4 = 69, C4 = 60. */
public final class Notes {
    private static final String[] SHARP = {"C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"};
    private static final String[] FLAT = {"C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B"};

    private Notes() {}

    public static double frequency(int midi, double a4) {
        return a4 * Math.pow(2.0, (midi - 69) / 12.0);
    }

    /** Signed distance in cents from {@code reference} to {@code freq}. */
    public static double cents(double freq, double reference) {
        return 1200.0 * Math.log(freq / reference) / Math.log(2.0);
    }

    public static String pitchClass(int midi, boolean flats) {
        int pc = ((midi % 12) + 12) % 12;
        return (flats ? FLAT : SHARP)[pc];
    }

    public static int octave(int midi) {
        return Math.floorDiv(midi, 12) - 1;
    }

    public static String name(int midi, boolean flats) {
        return pitchClass(midi, flats) + octave(midi);
    }

    public static int midiFor(int pitchClass, int octave) {
        return (octave + 1) * 12 + pitchClass;
    }

    public static String[] pitchClassNames() {
        return SHARP.clone();
    }
}
