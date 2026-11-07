package io.github.dsheirer.hackrf;

/** Opera Cake switching modes of {@code enum operacake_switching_mode}. */
public enum OperacakeMode {
    /** {@code OPERACAKE_MODE_MANUAL}: ports are set by {@link HackRfDevice#setOperacakePorts}. */
    MANUAL,
    /** {@code OPERACAKE_MODE_FREQUENCY}: ports follow the tuned frequency. */
    FREQUENCY,
    /** {@code OPERACAKE_MODE_TIME}: ports change after a number of samples. */
    TIME;

    /** Returns the native value. */
    public int code() {
        return ordinal();
    }

    /** Returns the mode for a native value. */
    static OperacakeMode fromCode(int code) {
        if (code < 0 || code >= values().length) {
            throw new IllegalArgumentException("Unknown Opera Cake mode: " + code);
        }
        return values()[code];
    }
}
