package io.github.dsheirer.hackrf;

/**
 * State of the sample loop on the device's M0 core, a {@code hackrf_m0_state}.
 * Modes are 0 (idle), 1 (wait), 2 (receive), 3 (transmit start) and 4
 * (transmit run).
 *
 * @param requestedMode requested mode
 * @param requestPending true while a mode request is pending
 * @param activeMode active mode
 * @param m0Count bytes transferred by the M0
 * @param m4Count bytes transferred by the M4
 * @param shortfalls number of shortfalls (overruns or underruns)
 * @param longestShortfall longest shortfall in bytes
 * @param shortfallLimit shortfall limit in bytes
 * @param threshold {@code m0Count} at which the mode changes to {@code nextMode}
 * @param nextMode mode to change to at the threshold
 * @param error error that returned the M0 to idle: 0 none, 1 receive timeout,
 *     2 transmit timeout, 3 missed deadline
 */
public record M0State(int requestedMode, boolean requestPending, long activeMode, long m0Count, long m4Count,
        long shortfalls, long longestShortfall, long shortfallLimit, long threshold, long nextMode, long error) {
}
