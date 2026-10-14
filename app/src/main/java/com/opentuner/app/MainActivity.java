package com.opentuner.app;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements PitchEngine.Listener {
    private static final int HISTORY = 5;
    private static final int SILENCE_FRAMES = 8;

    private TuningStore store;
    private PitchEngine engine;
    private final TonePlayer tonePlayer = new TonePlayer();

    private Instrument instrument;
    private Tuning tuning;
    private float a4;
    /** Manually locked string index, or -1 for auto-detect. */
    private int lockedString = -1;
    private int lastString = -1;

    private final float[] history = new float[HISTORY];
    private int historySize;
    private int silentFrames;

    private GaugeView gauge;
    private TextView txtNote, txtStatus, txtFreq, txtMode;
    private Button btnTuning, btnPlay, btnGrant;
    private LinearLayout stringsRow, permissionPanel;
    private MaterialToolbar toolbar;
    private MaterialButtonToggleGroup instrumentGroup;
    private final List<MaterialButton> stringButtons = new ArrayList<>();

    private boolean micGranted;
    private boolean started;

    private final ActivityResultLauncher<String> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                updatePermissionUi();
                if (granted) startListening();
            });

    private final ActivityResultLauncher<Intent> editorLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                Intent data = result.getData();
                if (data == null) return;
                String id = data.getStringExtra(TuningEditorActivity.RESULT_TUNING_ID);
                if (id != null) {
                    Tuning t = store.findTuning(id);
                    if (t != null) {
                        instrument = t.instrument;
                        instrumentGroup.check(instrument == Instrument.GUITAR ? R.id.btn_guitar : R.id.btn_bass);
                        selectTuning(t);
                    }
                } else if (data.getBooleanExtra(TuningEditorActivity.RESULT_DELETED, false)) {
                    selectTuning(Tunings.standard(instrument));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        store = new TuningStore(this);
        applyTheme(store.getTheme());
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        toolbar = findViewById(R.id.toolbar);
        toolbar.inflateMenu(R.menu.main);
        toolbar.setOnMenuItemClickListener(this::onMenuItem);
        gauge = findViewById(R.id.gauge);
        txtNote = findViewById(R.id.txt_note);
        txtStatus = findViewById(R.id.txt_status);
        txtFreq = findViewById(R.id.txt_freq);
        txtMode = findViewById(R.id.txt_mode);
        btnTuning = findViewById(R.id.btn_tuning);
        btnPlay = findViewById(R.id.btn_play);
        btnGrant = findViewById(R.id.btn_grant);
        stringsRow = findViewById(R.id.strings);
        permissionPanel = findViewById(R.id.permission_panel);
        instrumentGroup = findViewById(R.id.instrument_group);

        a4 = store.getA4();
        instrument = store.getInstrument();
        tuning = store.getSelectedTuning(instrument);
        instrumentGroup.check(instrument == Instrument.GUITAR ? R.id.btn_guitar : R.id.btn_bass);

        instrumentGroup.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            Instrument next = id == R.id.btn_guitar ? Instrument.GUITAR : Instrument.BASS;
            if (next == instrument) return;
            instrument = next;
            store.setInstrument(instrument);
            selectTuning(store.getSelectedTuning(instrument));
        });
        btnTuning.setOnClickListener(v -> showTuningPicker());
        btnPlay.setOnClickListener(v -> toggleTone());
        btnGrant.setOnClickListener(v -> requestMic());

        engine = new PitchEngine(this, this);
        selectTuning(tuning);
    }

    @Override
    protected void onStart() {
        super.onStart();
        updatePermissionUi();
        if (micGranted) startListening();
        else if (!permissionRequestedOnce) {
            permissionRequestedOnce = true;
            requestMic();
        }
    }

    private boolean permissionRequestedOnce;

    @Override
    protected void onStop() {
        super.onStop();
        tonePlayer.stop();
        stopListening();
    }

    // ---- permission ----

    private void updatePermissionUi() {
        micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        permissionPanel.setVisibility(micGranted ? View.GONE : View.VISIBLE);
        if (!micGranted && permissionRequestedOnce
                && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) {
            btnGrant.setText(R.string.open_settings);
        } else {
            btnGrant.setText(R.string.grant_mic);
        }
    }

    private void requestMic() {
        if (micGranted) return;
        if (permissionRequestedOnce && !shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)
                && btnGrant.getText().equals(getString(R.string.open_settings))) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null)));
            return;
        }
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
    }

    private void startListening() {
        if (!micGranted || started || tonePlayer.isPlaying()) return;
        started = true;
        engine.start();
    }

    private void stopListening() {
        if (!started) return;
        started = false;
        engine.stop();
        resetReading();
    }

    // ---- tuning selection ----

    private void selectTuning(Tuning t) {
        tuning = t;
        store.setSelectedTuning(t.instrument, t.id);
        lockedString = -1;
        lastString = -1;
        btnTuning.setText(t.name + "  ·  " + t.describe());
        toolbar.getMenu().findItem(R.id.action_edit).setVisible(!t.builtIn);
        buildStringButtons();
        tonePlayer.stop();
        resetReading();
    }

    private void buildStringButtons() {
        stringsRow.removeAllViews();
        stringButtons.clear();
        int margin = (int) (4 * getResources().getDisplayMetrics().density);
        for (int i = 0; i < tuning.notes.length; i++) {
            final int index = i;
            MaterialButton b = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setText(tuning.noteName(i));
            b.setCheckable(true);
            b.setAllCaps(false);
            b.setInsetTop(0);
            b.setInsetBottom(0);
            b.setPadding(0, 0, 0, 0);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setSingleLine(true);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(margin, 0, margin, 0);
            b.setLayoutParams(lp);
            b.setContentDescription("String " + (i + 1) + ", " + tuning.noteName(i));
            b.setOnClickListener(v -> {
                lockedString = lockedString == index ? -1 : index;
                refreshStringButtons(-1, false);
                resetReading();
            });
            stringButtons.add(b);
            stringsRow.addView(b);
        }
        refreshStringButtons(-1, false);
    }

    private void refreshStringButtons(int detected, boolean inTune) {
        for (int i = 0; i < stringButtons.size(); i++) {
            MaterialButton b = stringButtons.get(i);
            b.setChecked(i == lockedString);
            boolean highlight = i == detected;
            int stroke = highlight
                    ? ContextCompat.getColor(this, inTune ? R.color.in_tune : R.color.out_of_tune)
                    : com.google.android.material.color.MaterialColors.getColor(b,
                    com.google.android.material.R.attr.colorOutline);
            b.setStrokeColor(android.content.res.ColorStateList.valueOf(stroke));
            b.setStrokeWidth((int) ((highlight ? 3 : 1) * getResources().getDisplayMetrics().density));
        }
        if (lockedString >= 0) {
            txtMode.setText(getString(R.string.manual_mode, tuning.noteName(lockedString)));
        } else {
            txtMode.setText(R.string.auto_mode);
        }
    }

    private void showTuningPicker() {
        final List<Tuning> list = store.getTunings(instrument);
        String[] labels = new String[list.size()];
        int checked = -1;
        for (int i = 0; i < labels.length; i++) {
            Tuning t = list.get(i);
            labels[i] = t.name + (t.builtIn ? "" : "  (" + getString(R.string.custom_suffix) + ")")
                    + "\n" + t.describe();
            if (t.id.equals(tuning.id)) checked = i;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_tuning)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    selectTuning(list.get(which));
                    d.dismiss();
                })
                .setNeutralButton(R.string.new_custom, (d, w) -> openEditor(null))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openEditor(String id) {
        Intent i = new Intent(this, TuningEditorActivity.class);
        i.putExtra(TuningEditorActivity.EXTRA_INSTRUMENT, instrument.name());
        i.putExtra(TuningEditorActivity.EXTRA_NOTES, tuning.notes);
        if (id != null) i.putExtra(TuningEditorActivity.EXTRA_ID, id);
        editorLauncher.launch(i);
    }

    // ---- menu / settings ----

    private boolean onMenuItem(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            showSettings();
            return true;
        }
        if (item.getItemId() == R.id.action_edit) {
            if (!tuning.builtIn) openEditor(tuning.id);
            return true;
        }
        return false;
    }

    private void showSettings() {
        final float density = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * density);
        root.setPadding(pad, (int) (8 * density), pad, 0);

        final TextView a4Label = new TextView(this);
        a4Label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        a4Label.setText(getString(R.string.reference_pitch, a4));
        root.addView(a4Label);

        final SeekBar seek = new SeekBar(this);
        seek.setMax(Math.round((TuningStore.MAX_A4 - TuningStore.MIN_A4) * 10));
        seek.setProgress(Math.round((a4 - TuningStore.MIN_A4) * 10));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                a4Label.setText(getString(R.string.reference_pitch, TuningStore.MIN_A4 + progress / 10f));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        root.addView(seek);

        Button reset = new Button(this, null, android.R.attr.borderlessButtonStyle);
        reset.setText(R.string.reset);
        reset.setOnClickListener(v -> seek.setProgress(Math.round((TuningStore.DEFAULT_A4 - TuningStore.MIN_A4) * 10)));
        root.addView(reset);

        TextView themeLabel = new TextView(this);
        themeLabel.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        themeLabel.setText(R.string.theme);
        themeLabel.setPadding(0, (int) (12 * density), 0, 0);
        root.addView(themeLabel);

        final RadioGroup themes = new RadioGroup(this);
        int[] labels = {R.string.theme_system, R.string.theme_light, R.string.theme_dark};
        for (int i = 0; i < labels.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setId(1000 + i);
            rb.setText(labels[i]);
            themes.addView(rb);
        }
        themes.check(1000 + store.getTheme());
        root.addView(themes);

        new AlertDialog.Builder(this)
                .setTitle(R.string.settings)
                .setView(root)
                .setPositiveButton(R.string.ok, (d, w) -> {
                    a4 = TuningStore.MIN_A4 + seek.getProgress() / 10f;
                    store.setA4(a4);
                    resetReading();
                    int theme = themes.getCheckedRadioButtonId() - 1000;
                    if (theme != store.getTheme()) {
                        store.setTheme(theme);
                        applyTheme(theme);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private static void applyTheme(int theme) {
        AppCompatDelegate.setDefaultNightMode(theme == TuningStore.THEME_LIGHT
                ? AppCompatDelegate.MODE_NIGHT_NO
                : theme == TuningStore.THEME_DARK ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    // ---- reference tone ----

    private void toggleTone() {
        if (tonePlayer.isPlaying()) {
            tonePlayer.stop();
            return;
        }
        int index = lockedString >= 0 ? lockedString : lastString >= 0 ? lastString : 0;
        final double freq = Notes.frequency(tuning.notes[index], a4);
        // Stop listening so the speaker is not picked up as input.
        stopListening();
        btnPlay.setText(R.string.stop_tone);
        txtNote.setText(tuning.noteName(index));
        txtFreq.setText(String.format(Locale.US, "%.1f Hz", freq));
        tonePlayer.play(freq, () -> {
            btnPlay.setText(R.string.play_tone);
            if (!isFinishing() && getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                startListening();
            }
            resetReading();
        });
    }

    // ---- pitch handling ----

    @Override
    public void onPitch(float frequency) {
        if (frequency <= 0) {
            if (++silentFrames >= SILENCE_FRAMES) resetReading();
            return;
        }
        silentFrames = 0;

        // A big jump means a new note: forget the old history.
        if (historySize > 0 && Math.abs(Notes.cents(frequency, median())) > 80) historySize = 0;
        if (historySize < HISTORY) {
            history[historySize++] = frequency;
        } else {
            System.arraycopy(history, 1, history, 0, HISTORY - 1);
            history[HISTORY - 1] = frequency;
        }
        showPitch(median());
    }

    private float median() {
        float[] copy = Arrays.copyOf(history, historySize);
        Arrays.sort(copy);
        return copy[copy.length / 2];
    }

    private void showPitch(float f) {
        int target = lockedString;
        if (target < 0) {
            double best = Double.MAX_VALUE;
            for (int i = 0; i < tuning.notes.length; i++) {
                double d = Math.abs(Notes.cents(f, Notes.frequency(tuning.notes[i], a4)));
                if (d < best) {
                    best = d;
                    target = i;
                }
            }
        }
        lastString = target;
        double targetFreq = Notes.frequency(tuning.notes[target], a4);
        float cents = (float) Notes.cents(f, targetFreq);
        boolean good = Math.abs(cents) <= GaugeView.IN_TUNE_CENTS;

        gauge.setReading(true, cents);
        txtNote.setText(tuning.noteName(target));
        txtNote.setTextColor(good ? ContextCompat.getColor(this, R.color.in_tune) : txtFreq.getCurrentTextColor());
        txtStatus.setText(good ? R.string.in_tune : cents < 0 ? R.string.too_low : R.string.too_high);
        txtFreq.setText(String.format(Locale.US, "%.1f Hz  ·  %+.0f cents", f, cents));
        refreshStringButtons(target, good);
    }

    private void resetReading() {
        historySize = 0;
        silentFrames = 0;
        gauge.setReading(false, 0);
        txtNote.setText("–");
        txtNote.setTextColor(txtFreq.getCurrentTextColor());
        txtStatus.setText(R.string.listening);
        txtFreq.setText("");
        refreshStringButtons(-1, false);
    }

    @Override
    public void onError(String message) {
        started = false;
        txtStatus.setText(message);
    }
}
