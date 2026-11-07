package io.github.dsheirer.hackrf;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

/**
 * One block of a sweep: samples received at one tuning step.
 *
 * @param frequency frequency in Hz the device was tuned to
 * @param samples interleaved signed 8-bit I/Q pairs, valid only while the
 *     {@link SampleBuffer} it came from is
 */
public record SweepBlock(long frequency, MemorySegment samples) {
    /** Copies the samples to a new array: I, Q, I, Q, ... */
    public byte[] copyBytes() {
        return samples.toArray(ValueLayout.JAVA_BYTE);
    }
}
