package com.opentuner.app;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;

/** Create, edit or delete a custom tuning with one note picker per string. */
public class TuningEditorActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_INSTRUMENT = "instrument";
    public static final String EXTRA_NOTES = "notes";
    public static final String RESULT_TUNING_ID = "tuning_id";
    public static final String RESULT_DELETED = "deleted";

    private static final int MIN_OCTAVE = 0;
    private static final int MAX_OCTAVE = 6;

    private TuningStore store;
    private String editingId;
    private Instrument instrument;
    private TextInputEditText editName;
    private LinearLayout rows;
    private final List<Spinner> noteSpinners = new ArrayList<>();
    private final List<Spinner> octaveSpinners = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tuning_editor);
        store = new TuningStore(this);

        Intent intent = getIntent();
        editingId = intent.getStringExtra(EXTRA_ID);
        instrument = Instrument.valueOf(intent.getStringExtra(EXTRA_INSTRUMENT));
        int[] notes = intent.getIntArrayExtra(EXTRA_NOTES);
        Tuning existing = editingId == null ? null : store.findTuning(editingId);
        if (editingId != null && (existing == null || existing.builtIn)) {
            finish();
            return;
        }

        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(editingId == null ? R.string.editor_title_new : R.string.editor_title_edit);
        toolbar.setNavigationOnClickListener(v -> finish());

        editName = findViewById(R.id.edit_name);
        rows = findViewById(R.id.rows);
        MaterialButtonToggleGroup group = findViewById(R.id.instrument_group);
        group.check(instrument == Instrument.GUITAR ? R.id.btn_guitar : R.id.btn_bass);

        if (existing != null) {
            editName.setText(existing.name);
            notes = existing.notes;
        }
        if (notes == null || notes.length != instrument.stringCount) notes = Tunings.standard(instrument).notes;
        buildRows(notes);

        group.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            Instrument next = id == R.id.btn_guitar ? Instrument.GUITAR : Instrument.BASS;
            if (next == instrument) return;
            instrument = next;
            buildRows(Tunings.standard(instrument).notes);
        });

        findViewById(R.id.btn_save).setOnClickListener(v -> save());
        Button delete = findViewById(R.id.btn_delete);
        if (existing != null) {
            delete.setVisibility(View.VISIBLE);
            delete.setOnClickListener(v -> confirmDelete(existing));
        }
    }

    private void buildRows(int[] notes) {
        rows.removeAllViews();
        noteSpinners.clear();
        octaveSpinners.clear();
        ArrayAdapter<String> noteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                Notes.pitchClassNames());
        List<String> octaves = new ArrayList<>();
        for (int o = MIN_OCTAVE; o <= MAX_OCTAVE; o++) octaves.add(String.valueOf(o));
        ArrayAdapter<String> octaveAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, octaves);

        for (int i = 0; i < notes.length; i++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            TextView label = new TextView(this);
            label.setText(getString(R.string.string_n, i + 1));
            label.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f));

            Spinner note = new Spinner(this);
            note.setAdapter(noteAdapter);
            note.setSelection(((notes[i] % 12) + 12) % 12);
            row.addView(note, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            Spinner octave = new Spinner(this);
            octave.setAdapter(octaveAdapter);
            octave.setSelection(Math.max(MIN_OCTAVE, Math.min(MAX_OCTAVE, Notes.octave(notes[i]))) - MIN_OCTAVE);
            row.addView(octave, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            noteSpinners.add(note);
            octaveSpinners.add(octave);
            rows.addView(row);
        }
    }

    private void save() {
        String name = editName.getText() == null ? "" : editName.getText().toString().trim();
        if (name.isEmpty()) {
            editName.setError(getString(R.string.name_required));
            return;
        }
        int[] notes = new int[noteSpinners.size()];
        for (int i = 0; i < notes.length; i++) {
            notes[i] = Notes.midiFor(noteSpinners.get(i).getSelectedItemPosition(),
                    octaveSpinners.get(i).getSelectedItemPosition() + MIN_OCTAVE);
        }
        Tuning saved = store.saveCustom(editingId, name, instrument, notes);
        setResult(RESULT_OK, new Intent().putExtra(RESULT_TUNING_ID, saved.id));
        finish();
    }

    private void confirmDelete(Tuning t) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.delete_confirm, t.name))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    store.deleteCustom(t.id);
                    setResult(RESULT_OK, new Intent().putExtra(RESULT_DELETED, true));
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}
