package io.github.dsheirer.hackrf;

/** Mixer filter paths of {@code enum rf_path_filter}, for explicit tuning. */
public enum RfPathFilter {
    /** {@code RF_PATH_FILTER_BYPASS}: no mixing; the IF frequency is the center frequency. */
    BYPASS,
    /** {@code RF_PATH_FILTER_LOW_PASS}: low pass filter, center frequency is IF - LO. */
    LOW_PASS,
    /** {@code RF_PATH_FILTER_HIGH_PASS}: high pass filter, center frequency is IF + LO. */
    HIGH_PASS;

    /** Returns the native value. */
    public int code() {
        return ordinal();
    }
}
