package io.github.dsheirer.hackrf;

/**
 * Receives samples while a device is receiving or sweeping.
 *
 * <p>Runs on the library's USB event thread, which serves every open device, so
 * it should return quickly: copy the samples and hand them to another thread
 * for slow work. It must not call any method of a {@link HackRfDevice}; those
 * throw {@link IllegalStateException} here. Return false to stop instead. If
 * it throws, streaming stops and the exception is available from
 * {@link HackRfDevice#streamingFailure()}.
 */
@FunctionalInterface
public interface SampleListener {
    /**
     * Handles a block of samples.
     *
     * @return true to keep streaming, false to stop
     */
    boolean onSamples(SampleBuffer buffer);
}
