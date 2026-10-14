package com.opentuner.app;

import java.util.ArrayList;
import java.util.List;

/** Built-in tuning presets. */
public final class Tunings {
    private static final List<Tuning> ALL = new ArrayList<>();

    static {
        // Guitar (E2 A2 D3 G3 B3 E4 = 40 45 50 55 59 64)
        g("g_standard", "Standard", false, 40, 45, 50, 55, 59, 64);
        g("g_half_down", "Half-step down", true, 39, 44, 49, 54, 58, 63);
        g("g_full_down", "Full-step down", false, 38, 43, 48, 53, 57, 62);
        g("g_drop_d", "Drop D", false, 38, 45, 50, 55, 59, 64);
        g("g_double_drop_d", "Double Drop D", false, 38, 45, 50, 55, 59, 62);
        g("g_dadgad", "DADGAD", false, 38, 45, 50, 55, 57, 62);
        g("g_open_g", "Open G", false, 38, 43, 50, 55, 59, 62);
        g("g_open_d", "Open D", false, 38, 45, 50, 54, 57, 62);
        g("g_open_e", "Open E", false, 40, 47, 52, 56, 59, 64);
        g("g_open_a", "Open A", false, 40, 45, 52, 57, 61, 64);
        g("g_open_c", "Open C", false, 36, 43, 48, 55, 60, 64);

        // Bass (E1 A1 D2 G2 = 28 33 38 43)
        b("b_standard", "Standard", false, 28, 33, 38, 43);
        b("b_half_down", "Half-step down", true, 27, 32, 37, 42);
        b("b_full_down", "Full-step down", false, 26, 31, 36, 41);
        b("b_drop_d", "Drop D", false, 26, 33, 38, 43);
        b("b_drop_c", "Drop C", false, 24, 31, 36, 41);
        b("b_open_e", "Open E", false, 28, 35, 40, 44);
        b("b_open_d", "Open D", false, 26, 33, 38, 42);
        b("b_open_g", "Open G", false, 26, 31, 38, 43);
    }

    private Tunings() {}

    private static void g(String id, String name, boolean flats, int... notes) {
        ALL.add(new Tuning(id, name, Instrument.GUITAR, notes, true, flats));
    }

    private static void b(String id, String name, boolean flats, int... notes) {
        ALL.add(new Tuning(id, name, Instrument.BASS, notes, true, flats));
    }

    public static List<Tuning> forInstrument(Instrument instrument) {
        List<Tuning> out = new ArrayList<>();
        for (Tuning t : ALL) if (t.instrument == instrument) out.add(t);
        return out;
    }

    public static Tuning standard(Instrument instrument) {
        return instrument == Instrument.GUITAR ? find("g_standard") : find("b_standard");
    }

    public static Tuning find(String id) {
        for (Tuning t : ALL) if (t.id.equals(id)) return t;
        return null;
    }
}
