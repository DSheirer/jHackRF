package io.github.dsheirer.hackrf;

import java.util.Set;

/**
 * Board type read from a device, an {@code enum hackrf_board_id} value.
 *
 * @param code the native value, such as 2 for {@code BOARD_ID_HACKRF1_OG}
 * @param name the library's name for it, such as "HackRF One"
 * @param platforms the platform it belongs to; empty for an unknown board
 */
public record BoardId(int code, String name, Set<Platform> platforms) {
    /** {@code BOARD_ID_HACKRF1_OG}: HackRF One before revision r9. */
    public static final int HACKRF1_OG = 2;
    /** {@code BOARD_ID_HACKRF1_R9}: HackRF One r9 and later. */
    public static final int HACKRF1_R9 = 4;
    /** {@code BOARD_ID_PRALINE}: HackRF Pro. */
    public static final int PRALINE = 5;

    /** Creates a board ID; the set of platforms is copied. */
    public BoardId {
        platforms = Set.copyOf(platforms);
    }

    @Override
    public String toString() {
        return name + " (" + code + ")";
    }
}
