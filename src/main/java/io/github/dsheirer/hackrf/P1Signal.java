package io.github.dsheirer.hackrf;

/** Signals for the HackRF Pro P1 SMA connector, {@code enum p1_ctrl_signal}. */
public enum P1Signal {
    /** {@code P1_SIGNAL_TRIGGER_IN}. */
    TRIGGER_IN(0),
    /** {@code P1_SIGNAL_AUX_CLK1}. */
    AUX_CLK1(1),
    /** {@code P1_SIGNAL_CLKIN}. */
    CLKIN(2),
    /** {@code P1_SIGNAL_TRIGGER_OUT}. */
    TRIGGER_OUT(3),
    /** {@code P1_SIGNAL_P22_CLKIN}. */
    P22_CLKIN(4),
    /** {@code P1_SIGNAL_P2_5}. */
    P2_5(5),
    /** {@code P1_SIGNAL_NC}: not connected. */
    NC(6),
    /** {@code P1_SIGNAL_AUX_CLK2}. */
    AUX_CLK2(7);

    private final int code;

    P1Signal(int code) {
        this.code = code;
    }

    /** Returns the native value. */
    public int code() {
        return code;
    }
}
