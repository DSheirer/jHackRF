package io.github.dsheirer.hackrf;

/**
 * Hardware revision read from a device, an {@code enum hackrf_board_rev} value.
 *
 * @param code the native value
 * @param name the library's name for it, such as "r9"
 */
public record BoardRevision(int code, String name) {
    /** {@code BOARD_REV_HACKRF1_OLD}: a HackRF One older than r6. */
    public static final int HACKRF1_OLD = 0;
    /** {@code BOARD_REV_UNRECOGNIZED}. */
    public static final int UNRECOGNIZED = 0xFE;
    /** {@code BOARD_REV_UNDETECTED}. */
    public static final int UNDETECTED = 0xFF;

    /** {@code BOARD_REV_GSG}: set on boards made by Great Scott Gadgets. */
    public static final int GSG_FLAG = 0x80;

    /** Returns true if Great Scott Gadgets made this board. */
    public boolean isGreatScottGadgets() {
        return code != UNRECOGNIZED && code != UNDETECTED && (code & GSG_FLAG) != 0;
    }

    @Override
    public String toString() {
        return name + " (" + code + ")";
    }
}
