package io.github.dsheirer.hackrf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Receive-only tests against a real device. Enable with {@code -PhardwareTest=true}.
 * Nothing is transmitted and nothing is written to the device's flash memory.
 */
@EnabledIfSystemProperty(named = "hackrf.hardware.test", matches = "true")
class HardwareTest {
    private static final long FREQUENCY = 100_000_000L;
    private static final double SAMPLE_RATE = 10_000_000;

    private static void configure(HackRfDevice device) throws HackRfException {
        device.setSampleRate(SAMPLE_RATE);
        device.setFrequency(FREQUENCY);
        device.setAmpEnable(false);
        device.setLnaGain(16);
        device.setVgaGain(20);
    }

    @Test
    void readsDeviceInformation() throws HackRfException {
        List<DeviceEntry> devices = HackRf.listDevices();
        assertFalse(devices.isEmpty(), "No device found");
        DeviceEntry entry = devices.getFirst();
        try (HackRfDevice device = HackRf.open(entry.index())) {
            BoardInfo info = device.boardInfo();
            assertEquals(32, info.serialNumber().length());
            if (entry.serialNumber() != null) {
                assertEquals(entry.serialNumber(), info.serialNumber());
            }
            assertFalse(device.firmwareVersion().isBlank());
            assertFalse(device.boardId().platforms().isEmpty(), device.boardId().toString());
            assertTrue(device.usbApiVersion() >= 0x0100);
            assertEquals(262144, device.transferBufferSize());
            assertEquals(4, device.transferQueueDepth());
            if (device.usbApiVersion() >= 0x0106) {
                device.boardRevision();
                assertFalse(device.supportedPlatforms().isEmpty());
                device.m0State();
            }
        }
        if (entry.serialNumber() != null) {
            try (HackRfDevice device = HackRf.open(entry.serialNumber())) {
                assertEquals(entry.serialNumber(), device.boardInfo().serialNumber());
            }
            // A suffix of the serial number is enough.
            try (HackRfDevice device = HackRf.open(entry.serialNumber().substring(16))) {
                assertEquals(entry.serialNumber(), device.boardInfo().serialNumber());
            }
        }
        HackRfException e = assertThrows(HackRfException.class, () -> HackRf.open(Integer.MAX_VALUE));
        assertEquals(HackRfError.NOT_FOUND, e.error());
    }

    @Test
    void appliesSettings() throws HackRfException {
        try (HackRfDevice device = HackRf.open()) {
            configure(device);
            device.setSampleRateManual(20_000_000L, 2);
            device.setBasebandFilterBandwidth(HackRf.computeBasebandFilterBandwidth(7_500_000L));
            device.setFrequency(433_920_000L);
            device.setHwSyncMode(false);
            HackRfException e = assertThrows(HackRfException.class, () -> device.setLnaGain(41));
            assertEquals(HackRfError.INVALID_PARAM, e.error());
            assertThrows(IllegalArgumentException.class, () -> device.setLnaGain(-1));
            assertThrows(IllegalArgumentException.class, () -> device.setSampleRateManual(20_000_000L, 0));
        }
    }

    @Test
    void streamsSamplesAndStops() throws Exception {
        try (HackRfDevice device = HackRf.open()) {
            configure(device);
            int expected = device.transferBufferSize();
            CountDownLatch received = new CountDownLatch(50);
            AtomicReference<byte[]> last = new AtomicReference<>();
            AtomicInteger lengths = new AtomicInteger();
            device.startRx(buffer -> {
                lengths.set(buffer.byteCount());
                last.set(buffer.copyBytes());
                received.countDown();
                return true;
            });
            assertTrue(received.await(10, TimeUnit.SECONDS), "Samples did not arrive");
            assertTrue(device.isStreaming());
            assertThrows(IllegalStateException.class, () -> device.startRx(buffer -> true));
            device.stopRx();
            assertFalse(device.isStreaming());
            assertNull(device.streamingFailure());
            assertEquals(expected, lengths.get());
            boolean nonZero = false;
            for (byte sample : last.get()) {
                nonZero |= sample != 0;
            }
            assertTrue(nonZero, "Samples were all zero");
        }
    }

    @Test
    void canRestartAfterListenerEndedStream() throws Exception {
        try (HackRfDevice device = HackRf.open()) {
            configure(device);
            for (int run = 0; run < 3; run++) {
                CountDownLatch received = new CountDownLatch(1);
                device.startRx(buffer -> {
                    received.countDown();
                    return false;
                });
                assertTrue(received.await(10, TimeUnit.SECONDS), "No samples in run " + run);
                for (int i = 0; i < 200 && device.isStreaming(); i++) {
                    Thread.sleep(5);
                }
            }
            device.stopRx();
            assertNull(device.streamingFailure());
        }
    }

    @Test
    void sweeps() throws Exception {
        try (HackRfDevice device = HackRf.open()) {
            if (device.usbApiVersion() < 0x0104) {
                return;
            }
            configure(device);
            device.setSampleRateManual(20_000_000L, 1);
            device.setBasebandFilterBandwidth(15_000_000L);
            device.initSweep(new int[] {2400, 2490}, 16384 * 20, 20_000_000L, 7_500_000L, SweepStyle.INTERLEAVED);
            CountDownLatch received = new CountDownLatch(20);
            AtomicReference<Long> frequency = new AtomicReference<>();
            device.startRxSweep(buffer -> {
                for (SweepBlock block : buffer.sweepBlocks()) {
                    frequency.set(block.frequency());
                }
                received.countDown();
                return true;
            });
            assertTrue(received.await(10, TimeUnit.SECONDS), "Sweep data did not arrive");
            device.stopRx();
            assertNull(device.streamingFailure());
            long last = frequency.get();
            assertTrue(last >= 2_390_000_000L && last <= 2_520_000_000L, "Frequency " + last);
        }
    }

    @Test
    void listenerExceptionStopsStreamInsteadOfCrashing() throws Exception {
        try (HackRfDevice device = HackRf.open()) {
            configure(device);
            CountDownLatch called = new CountDownLatch(1);
            device.startRx(buffer -> {
                called.countDown();
                throw new IllegalArgumentException("expected");
            });
            assertTrue(called.await(10, TimeUnit.SECONDS));
            for (int i = 0; i < 100 && device.streamingFailure() == null; i++) {
                Thread.sleep(10);
            }
            assertInstanceOf(IllegalArgumentException.class, device.streamingFailure());
            device.stopRx();
        }
    }

    @Test
    void samplesCannotBeUsedAfterCallbackReturns() throws Exception {
        try (HackRfDevice device = HackRf.open()) {
            configure(device);
            AtomicReference<MemorySegment> kept = new AtomicReference<>();
            CountDownLatch called = new CountDownLatch(1);
            device.startRx(buffer -> {
                kept.compareAndSet(null, buffer.samples());
                called.countDown();
                return false;
            });
            assertTrue(called.await(10, TimeUnit.SECONDS));
            device.stopRx();
            assertThrows(RuntimeException.class, () -> kept.get().get(ValueLayout.JAVA_BYTE, 0));
        }
    }

    @Test
    @SuppressWarnings("try")
    void deviceCallsFromListenerAreRejected() throws Exception {
        try (HackRfDevice device = HackRf.open()) {
            configure(device);
            AtomicReference<Throwable> closing = new AtomicReference<>();
            AtomicReference<Throwable> tuning = new AtomicReference<>();
            CountDownLatch called = new CountDownLatch(1);
            device.startRx(buffer -> {
                if (called.getCount() > 0) {
                    try {
                        device.close();
                    } catch (Throwable t) {
                        closing.set(t);
                    }
                    try {
                        device.setFrequency(FREQUENCY);
                    } catch (Throwable t) {
                        tuning.set(t);
                    }
                    called.countDown();
                }
                return false;
            });
            assertTrue(called.await(10, TimeUnit.SECONDS));
            assertInstanceOf(IllegalStateException.class, closing.get());
            assertInstanceOf(IllegalStateException.class, tuning.get());
        }
    }

    @Test
    void exitIsRefusedWhileDeviceIsOpen() throws HackRfException {
        try (HackRfDevice device = HackRf.open()) {
            HackRfException e = assertThrows(HackRfException.class, HackRf::exit);
            assertEquals(HackRfError.NOT_LAST_DEVICE, e.error());
            assertFalse(device.isStreaming());
        }
        HackRf.exit();
        try (HackRfDevice device = HackRf.open()) {
            assertFalse(device.firmwareVersion().isBlank());
        }
    }
}
