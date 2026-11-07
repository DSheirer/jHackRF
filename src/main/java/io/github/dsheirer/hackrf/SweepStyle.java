package io.github.dsheirer.hackrf;

/** Sweep styles of {@code enum sweep_style}. */
public enum SweepStyle {
    /** {@code LINEAR}: step width is added to the frequency at each step. */
    LINEAR,
    /**
     * {@code INTERLEAVED}: each step is divided into two interleaved sub-steps,
     * which lets the host select the best portions of the FFT of each sub-step.
     */
    INTERLEAVED;

    /** Returns the native value. */
    public int code() {
        return ordinal();
    }
}
