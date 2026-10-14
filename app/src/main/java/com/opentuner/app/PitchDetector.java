package com.opentuner.app;

/**
 * Monophonic pitch detection using the YIN algorithm
 * (de Cheveigné &amp; Kawahara, 2002).
 */
public final class PitchDetector {
    private static final float THRESHOLD = 0.15f;
    private static final float MIN_RMS = 0.006f;

    private final int sampleRate;
    private final int window;
    private final int maxTau;
    private final int minTau;
    private final float[] diff;

    /**
     * @param sampleRate sample rate in Hz
     * @param window     integration window; input must hold at least {@code window + maxTau} samples
     * @param minFreq    lowest detectable frequency
     * @param maxFreq    highest detectable frequency
     */
    public PitchDetector(int sampleRate, int window, float minFreq, float maxFreq) {
        this.sampleRate = sampleRate;
        this.window = window;
        this.maxTau = (int) (sampleRate / minFreq);
        this.minTau = Math.max(2, (int) (sampleRate / maxFreq));
        this.diff = new float[maxTau + 1];
    }

    public int requiredSamples() {
        return window + maxTau + 1;
    }

    /** Returns the fundamental frequency in Hz, or -1 when no clear pitch is present. */
    public float detect(float[] x) {
        double energy = 0;
        for (int i = 0; i < window; i++) energy += x[i] * x[i];
        if (Math.sqrt(energy / window) < MIN_RMS) return -1f;

        // Difference function.
        for (int tau = 1; tau <= maxTau; tau++) {
            float sum = 0;
            for (int j = 0; j < window; j++) {
                float d = x[j] - x[j + tau];
                sum += d * d;
            }
            diff[tau] = sum;
        }

        // Cumulative mean normalised difference.
        diff[0] = 1f;
        float running = 0;
        for (int tau = 1; tau <= maxTau; tau++) {
            running += diff[tau];
            diff[tau] = running == 0 ? 1f : diff[tau] * tau / running;
        }

        // Absolute threshold: first dip below the threshold, followed down to its local minimum.
        int best = -1;
        for (int tau = minTau; tau < maxTau; tau++) {
            if (diff[tau] < THRESHOLD) {
                while (tau + 1 < maxTau && diff[tau + 1] < diff[tau]) tau++;
                best = tau;
                break;
            }
        }
        if (best < 0) return -1f;

        // Parabolic interpolation around the minimum.
        float refined = best;
        if (best > 1 && best < maxTau) {
            float s0 = diff[best - 1], s1 = diff[best], s2 = diff[best + 1];
            float denom = 2f * (2f * s1 - s2 - s0);
            if (denom != 0f) refined = best + (s2 - s0) / denom;
        }
        return sampleRate / refined;
    }
}
