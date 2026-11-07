package io.github.dsheirer.hackrf;

/** Signals for the HackRF Pro P2 SMA connector, {@code enum p2_ctrl_signal}. */
public enum P2Signal {
    /** {@code P2_SIGNAL_CLK3}. */
    CLK3(0),
    /** {@code P2_SIGNAL_TRIGGER_IN}. */
    TRIGGER_IN(2),
    /** {@code P2_SIGNAL_TRIGGER_OUT}. */
    TRIGGER_OUT(3);

    private final int code;

    P2Signal(int code) {
        this.code = code;
    }

    /** Returns the native value. */
    public int code() {
        return code;
    }
}
