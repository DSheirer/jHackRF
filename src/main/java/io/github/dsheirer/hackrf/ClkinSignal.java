package io.github.dsheirer.hackrf;

/** Sources for the HackRF Pro clock input, {@code enum clkin_ctrl_signal}. */
public enum ClkinSignal {
    /** {@code CLKIN_SIGNAL_P1}: the P1 connector. */
    P1,
    /** {@code CLKIN_SIGNAL_P22}: header P22. */
    P22;

    /** Returns the native value. */
    public int code() {
        return ordinal();
    }
}
