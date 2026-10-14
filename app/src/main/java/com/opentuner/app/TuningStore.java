package com.opentuner.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Persists user settings and custom tunings in SharedPreferences. */
public final class TuningStore {
    public static final float DEFAULT_A4 = 440f;
    public static final float MIN_A4 = 415f;
    public static final float MAX_A4 = 466f;

    public static final int THEME_SYSTEM = 0;
    public static final int THEME_LIGHT = 1;
    public static final int THEME_DARK = 2;

    private static final String PREFS = "opentuner";
    private static final String KEY_CUSTOM = "custom_tunings";
    private static final String KEY_A4 = "a4";
    private static final String KEY_INSTRUMENT = "instrument";
    private static final String KEY_TUNING = "tuning_";
    private static final String KEY_THEME = "theme";

    private final SharedPreferences prefs;

    public TuningStore(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---- settings ----

    public float getA4() {
        return prefs.getFloat(KEY_A4, DEFAULT_A4);
    }

    public void setA4(float a4) {
        prefs.edit().putFloat(KEY_A4, a4).apply();
    }

    public Instrument getInstrument() {
        try {
            return Instrument.valueOf(prefs.getString(KEY_INSTRUMENT, Instrument.GUITAR.name()));
        } catch (IllegalArgumentException e) {
            return Instrument.GUITAR;
        }
    }

    public void setInstrument(Instrument instrument) {
        prefs.edit().putString(KEY_INSTRUMENT, instrument.name()).apply();
    }

    public int getTheme() {
        return prefs.getInt(KEY_THEME, THEME_SYSTEM);
    }

    public void setTheme(int theme) {
        prefs.edit().putInt(KEY_THEME, theme).apply();
    }

    public void setSelectedTuning(Instrument instrument, String tuningId) {
        prefs.edit().putString(KEY_TUNING + instrument.name(), tuningId).apply();
    }

    public Tuning getSelectedTuning(Instrument instrument) {
        String id = prefs.getString(KEY_TUNING + instrument.name(), null);
        Tuning t = id == null ? null : findTuning(id);
        return t != null && t.instrument == instrument ? t : Tunings.standard(instrument);
    }

    // ---- tunings ----

    public Tuning findTuning(String id) {
        Tuning t = Tunings.find(id);
        if (t != null) return t;
        for (Tuning c : getCustomTunings()) if (c.id.equals(id)) return c;
        return null;
    }

    /** Built-in presets followed by the user's custom tunings for the instrument. */
    public List<Tuning> getTunings(Instrument instrument) {
        List<Tuning> out = new ArrayList<>(Tunings.forInstrument(instrument));
        for (Tuning c : getCustomTunings()) if (c.instrument == instrument) out.add(c);
        return out;
    }

    public List<Tuning> getCustomTunings() {
        List<Tuning> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_CUSTOM, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Instrument instrument = Instrument.valueOf(o.getString("instrument"));
                JSONArray n = o.getJSONArray("notes");
                int[] notes = new int[n.length()];
                for (int j = 0; j < notes.length; j++) notes[j] = n.getInt(j);
                if (notes.length != instrument.stringCount) continue;
                out.add(new Tuning(o.getString("id"), o.getString("name"), instrument, notes, false, false));
            }
        } catch (JSONException | IllegalArgumentException e) {
            // Corrupt data: ignore what cannot be parsed.
        }
        return out;
    }

    /** Creates or replaces a custom tuning. Pass a null id to create a new one. Returns the saved tuning. */
    public Tuning saveCustom(String id, String name, Instrument instrument, int[] notes) {
        String finalId = id != null ? id : "c_" + UUID.randomUUID();
        Tuning saved = new Tuning(finalId, name, instrument, notes, false, false);
        List<Tuning> all = getCustomTunings();
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(finalId)) {
                all.set(i, saved);
                replaced = true;
            }
        }
        if (!replaced) all.add(saved);
        writeCustom(all);
        return saved;
    }

    public void deleteCustom(String id) {
        List<Tuning> all = getCustomTunings();
        for (int i = all.size() - 1; i >= 0; i--) if (all.get(i).id.equals(id)) all.remove(i);
        writeCustom(all);
    }

    private void writeCustom(List<Tuning> list) {
        JSONArray arr = new JSONArray();
        try {
            for (Tuning t : list) {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("name", t.name);
                o.put("instrument", t.instrument.name());
                JSONArray n = new JSONArray();
                for (int note : t.notes) n.put(note);
                o.put("notes", n);
                arr.put(o);
            }
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
        prefs.edit().putString(KEY_CUSTOM, arr.toString()).apply();
    }
}
