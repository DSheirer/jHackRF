package io.github.dsheirer.hackrf;

import java.util.EnumSet;
import java.util.Set;

/** Hardware platforms, the {@code HACKRF_PLATFORM_*} bits. */
public enum Platform {
    /** {@code HACKRF_PLATFORM_JAWBREAKER}. */
    JAWBREAKER(1),
    /** {@code HACKRF_PLATFORM_HACKRF1_OG}: HackRF One before revision r9. */
    HACKRF1_OG(1 << 1),
    /** {@code HACKRF_PLATFORM_RAD1O}. */
    RAD1O(1 << 2),
    /** {@code HACKRF_PLATFORM_HACKRF1_R9}: HackRF One r9 and later. */
    HACKRF1_R9(1 << 3),
    /** {@code HACKRF_PLATFORM_PRALINE}: HackRF Pro. */
    PRALINE(1 << 4);

    private final int bit;

    Platform(int bit) {
        this.bit = bit;
    }

    /** Returns this platform's bit. */
    public int bit() {
        return bit;
    }

    /** Decodes a platform bit field; unknown bits are ignored. */
    public static Set<Platform> fromMask(long mask) {
        Set<Platform> platforms = EnumSet.noneOf(Platform.class);
        for (Platform platform : values()) {
            if ((mask & platform.bit) != 0) {
                platforms.add(platform);
            }
        }
        return platforms;
    }
}
