package io.github.dsheirer.hackrf;

import java.util.Objects;

/**
 * One range of {@link OperacakeMode#FREQUENCY} switching, a
 * {@code hackrf_operacake_freq_range}.
 *
 * @param minMhz start of the range in MHz, 0 to 65535
 * @param maxMhz end of the range in MHz, 0 to 65535
 * @param port port to connect A0 to for frequencies in the range; B0 mirrors it
 */
public record OperacakeFrequencyRange(int minMhz, int maxMhz, OperacakePort port) {
    /** Validates the values. */
    public OperacakeFrequencyRange {
        if (minMhz < 0 || maxMhz > 0xFFFF || minMhz > maxMhz) {
            throw new IllegalArgumentException("Invalid frequency range: " + minMhz + " to " + maxMhz + " MHz");
        }
        Objects.requireNonNull(port, "port");
    }
}
