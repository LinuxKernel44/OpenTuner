package com.opentuner.app;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Handler;
import android.os.Looper;

/** Plays a plucked-string-like reference tone. */
public final class TonePlayer {
    private static final int SAMPLE_RATE = 44100;
    private static final double SECONDS = 2.5;
    // Relative harmonic amplitudes: low notes are hard to hear on phone speakers without overtones.
    private static final double[] HARMONICS = {1.0, 0.6, 0.4, 0.25, 0.12};

    private final Handler main = new Handler(Looper.getMainLooper());
    private AudioTrack track;
    private Runnable onDone;

    public boolean isPlaying() {
        return track != null;
    }

    /** Plays the given frequency; {@code done} runs on the main thread when playback ends or is stopped. */
    public void play(double frequency, Runnable done) {
        stop();
        int count = (int) (SAMPLE_RATE * SECONDS);
        short[] pcm = new short[count];
        double norm = 0;
        for (double h : HARMONICS) norm += h;
        for (int i = 0; i < count; i++) {
            double t = i / (double) SAMPLE_RATE;
            double env = Math.min(1.0, t / 0.01) * Math.exp(-1.3 * t);
            double s = 0;
            for (int h = 0; h < HARMONICS.length; h++) {
                double f = frequency * (h + 1);
                if (f < SAMPLE_RATE / 2.0) s += HARMONICS[h] * Math.sin(2 * Math.PI * f * t);
            }
            pcm[i] = (short) (s / norm * env * 0.8 * Short.MAX_VALUE);
        }

        AudioTrack t = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.length * 2)
                .build();
        t.write(pcm, 0, pcm.length);
        t.setNotificationMarkerPosition(count);
        t.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
            @Override
            public void onMarkerReached(AudioTrack at) {
                if (track == at) stop();
            }

            @Override
            public void onPeriodicNotification(AudioTrack at) {
            }
        }, main);
        track = t;
        onDone = done;
        t.play();
    }

    public void stop() {
        AudioTrack t = track;
        Runnable done = onDone;
        track = null;
        onDone = null;
        if (t == null) return;
        try {
            t.stop();
        } catch (IllegalStateException ignored) {
            // Already stopped.
        }
        t.release();
        if (done != null) done.run();
    }
}
