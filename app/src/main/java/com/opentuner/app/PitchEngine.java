package com.opentuner.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

/** Captures microphone audio on a worker thread and reports detected pitches on the main thread. */
public final class PitchEngine {
    public interface Listener {
        /** @param frequency detected frequency in Hz, or -1 when there is no clear pitch */
        void onPitch(float frequency);

        void onError(String message);
    }

    private static final int SAMPLE_RATE = 44100;
    private static final int WINDOW = 2048;
    private static final int HOP = 1024;
    private static final float MIN_FREQ = 27f;   // below a drop-C bass
    private static final float MAX_FREQ = 1400f; // above the high E string's harmonics region

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running;
    private Thread thread;

    public PitchEngine(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        thread = new Thread(this::run, "OpenTuner-audio");
        thread.start();
    }

    public synchronized void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    @SuppressLint("MissingPermission") // Checked by the caller before start().
    private void run() {
        PitchDetector detector = new PitchDetector(SAMPLE_RATE, WINDOW, MIN_FREQ, MAX_FREQ);
        int needed = detector.requiredSamples();

        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        AudioRecord record = null;
        try {
            record = new AudioRecord(selectSource(), SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, Math.max(minBuffer, HOP * 8));
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                postError("Could not open the microphone.");
                return;
            }
            record.startRecording();

            short[] chunk = new short[HOP];
            float[] ring = new float[needed];
            float[] frame = new float[needed];
            int filled = 0;

            while (running) {
                int n = record.read(chunk, 0, HOP);
                if (n <= 0) {
                    if (n < 0) {
                        postError("Microphone read failed.");
                        return;
                    }
                    continue;
                }
                // Slide the ring buffer left and append the new samples.
                System.arraycopy(ring, n, ring, 0, needed - n);
                for (int i = 0; i < n; i++) ring[needed - n + i] = chunk[i] / 32768f;
                filled = Math.min(needed, filled + n);
                if (filled < needed) continue;

                System.arraycopy(ring, 0, frame, 0, needed);
                final float f = detector.detect(frame);
                main.post(() -> {
                    if (running) listener.onPitch(f);
                });
            }
        } catch (RuntimeException e) {
            postError("Audio error: " + e.getMessage());
        } finally {
            if (record != null) {
                try {
                    record.stop();
                } catch (IllegalStateException ignored) {
                    // Never started.
                }
                record.release();
            }
        }
    }

    private int selectSource() {
        AudioManager am = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        boolean unprocessed = am != null
                && "true".equals(am.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED));
        return unprocessed ? MediaRecorder.AudioSource.UNPROCESSED : MediaRecorder.AudioSource.MIC;
    }

    private void postError(String message) {
        main.post(() -> listener.onError(message));
    }
}
