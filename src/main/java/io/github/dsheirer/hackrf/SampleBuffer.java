package io.github.dsheirer.hackrf;

import io.github.dsheirer.hackrf.internal.Layouts;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Samples delivered to a {@link SampleListener}: interleaved signed 8-bit I/Q
 * pairs, I first.
 *
 * <p>{@link #samples()} points at memory owned by the native library. It may be
 * used only on the callback's thread and only until the callback returns; after
 * that, access throws {@link IllegalStateException}. Use one of the copying
 * methods, or {@link MemorySegment#copy}, to keep the data.
 *
 * <p>In sweep mode the buffer holds several blocks, each starting with the
 * frequency it was received at; see {@link #sweepBlocks()}.
 *
 * @param samples sample memory, {@code 2 * sampleCount()} bytes long
 */
public record SampleBuffer(MemorySegment samples) {
    /** Bytes in each sweep block, {@code BYTES_PER_BLOCK}, including its header. */
    public static final int SWEEP_BLOCK_SIZE = Layouts.BYTES_PER_BLOCK;

    /** Bytes in the header at the start of each sweep block. */
    public static final int SWEEP_HEADER_SIZE = 10;

    /** Returns the number of bytes, two per I/Q pair. */
    public int byteCount() {
        return (int) samples.byteSize();
    }

    /** Returns the number of I/Q pairs. */
    public int sampleCount() {
        return byteCount() / 2;
    }

    /** Copies the samples to a new array: I, Q, I, Q, ... */
    public byte[] copyBytes() {
        return samples.toArray(ValueLayout.JAVA_BYTE);
    }

    /** Copies the samples into an array, which must hold at least {@link #byteCount()} bytes. */
    public void copyBytes(byte[] destination) {
        MemorySegment.copy(samples, ValueLayout.JAVA_BYTE, 0, destination, 0, byteCount());
    }

    /**
     * Converts the samples to floats from -1 to just under 1, dividing by 128:
     * I, Q, I, Q, ...
     */
    public float[] copyFloats() {
        float[] result = new float[byteCount()];
        copyFloats(result);
        return result;
    }

    /**
     * Converts the samples into an array of floats, which must hold at least
     * {@link #byteCount()} values, dividing by 128.
     */
    public void copyFloats(float[] destination) {
        byte[] bytes = copyBytes();
        if (destination.length < bytes.length) {
            throw new IndexOutOfBoundsException("Destination holds " + destination.length + " floats, not "
                + bytes.length);
        }
        for (int i = 0; i < bytes.length; i++) {
            destination[i] = bytes[i] / 128f;
        }
    }

    /**
     * Splits a buffer received in sweep mode into its blocks. Each block of
     * {@value #SWEEP_BLOCK_SIZE} bytes starts with {@code 0x7F 0x7F} and the
     * tuned frequency as a little-endian 64-bit value; blocks without that
     * header are skipped. The returned segments are only valid as long as
     * {@link #samples()} is.
     */
    public List<SweepBlock> sweepBlocks() {
        List<SweepBlock> blocks = new ArrayList<>();
        long size = samples.byteSize();
        for (long start = 0; start + SWEEP_BLOCK_SIZE <= size; start += SWEEP_BLOCK_SIZE) {
            if (samples.get(ValueLayout.JAVA_BYTE, start) != 0x7F
                    || samples.get(ValueLayout.JAVA_BYTE, start + 1) != 0x7F) {
                continue;
            }
            long frequency = samples.get(ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN),
                start + 2);
            blocks.add(new SweepBlock(frequency,
                samples.asSlice(start + SWEEP_HEADER_SIZE, SWEEP_BLOCK_SIZE - SWEEP_HEADER_SIZE)));
        }
        return blocks;
    }
}
