package io.github.dsheirer.hackrf;

/** Opera Cake antenna switch ports of {@code enum operacake_ports}. */
public enum OperacakePort {
    /** {@code OPERACAKE_PA1}. */
    PA1,
    /** {@code OPERACAKE_PA2}. */
    PA2,
    /** {@code OPERACAKE_PA3}. */
    PA3,
    /** {@code OPERACAKE_PA4}. */
    PA4,
    /** {@code OPERACAKE_PB1}. */
    PB1,
    /** {@code OPERACAKE_PB2}. */
    PB2,
    /** {@code OPERACAKE_PB3}. */
    PB3,
    /** {@code OPERACAKE_PB4}. */
    PB4;

    /** Returns the native value. */
    public int code() {
        return ordinal();
    }
}
