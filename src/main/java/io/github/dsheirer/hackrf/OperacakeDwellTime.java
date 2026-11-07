package io.github.dsheirer.hackrf;

import java.util.Objects;

/**
 * One step of {@link OperacakeMode#TIME} switching, a
 * {@code hackrf_operacake_dwell_time}.
 *
 * @param dwellSamples number of samples to stay on the port, 1 to 2^32 - 1
 * @param port port to connect A0 to; B0 mirrors it
 */
public record OperacakeDwellTime(long dwellSamples, OperacakePort port) {
    /** Validates the values. */
    public OperacakeDwellTime {
        if (dwellSamples < 1 || dwellSamples > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Dwell time out of range: " + dwellSamples);
        }
        Objects.requireNonNull(port, "port");
    }
}
