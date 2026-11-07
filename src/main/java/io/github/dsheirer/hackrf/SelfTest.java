package io.github.dsheirer.hackrf;

/**
 * Result of the device's power-on self-test, a {@code hackrf_selftest}.
 *
 * @param pass true if every test passed
 * @param message the device's report
 */
public record SelfTest(boolean pass, String message) {
}
