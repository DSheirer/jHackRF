package io.github.dsheirer.hackrf;

import java.lang.foreign.MemorySegment;

/**
 * Supplies samples while a device is transmitting.
 *
 * <p>The first calls happen on the thread that calls
 * {@link HackRfDevice#startTx(TransmitSource)}, to fill every transfer before
 * the first is sent; later calls happen on the library's USB event thread. Calls
 * never overlap. The rules of {@link SampleListener} apply: return quickly, do
 * not call any method of a {@link HackRfDevice}, and an exception stops the
 * transmission.
 */
@FunctionalInterface
public interface TransmitSource {
    /**
     * Fills a buffer with interleaved signed 8-bit I/Q pairs.
     *
     * <p>The buffer is valid only until this method returns. A short buffer is
     * padded with zeros to a multiple of 512 bytes.
     *
     * @param buffer memory to write samples to, {@link HackRfDevice#transferBufferSize()} bytes
     * @return the number of bytes written, or 0 (or less) to end the
     *     transmission; the samples of earlier calls are still sent
     */
    int fill(MemorySegment buffer);

    /**
     * Called after each transfer has gone to the device, on the USB event
     * thread, with whether it succeeded. Does nothing by default.
     */
    default void onTransferComplete(boolean success) {
    }
}
