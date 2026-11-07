package io.github.dsheirer.hackrf;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.dsheirer.hackrf.internal.Layouts;
import io.github.dsheirer.hackrf.internal.Native;
import io.github.dsheirer.hackrf.internal.NativeLibraryLoader;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Tests that need the native library for the current platform but no hardware. */
class LibraryTest {
    @Test
    void structureSizesMatchHeader() {
        assertEquals(40, Layouts.TRANSFER.byteSize());
        assertEquals(20, Layouts.offset(Layouts.TRANSFER, "valid_length"));
        assertEquals(24, Layouts.PART_SERIAL.byteSize());
        assertEquals(8, Layouts.OPERACAKE_DWELL_TIME.byteSize());
        assertEquals(6, Layouts.OPERACAKE_FREQ_RANGE.byteSize());
        assertEquals(9, Layouts.BIAS_T_USER_SETTING_REQ.byteSize());
        assertEquals(6, Layouts.offset(Layouts.BIAS_T_USER_SETTING_REQ, "off", "do_update"));
        assertEquals(40, Layouts.M0_STATE.byteSize());
        assertEquals(4096, Layouts.SELFTEST.byteSize());
        assertEquals(48, Layouts.DEVICE_LIST.byteSize());
        assertEquals(32, Layouts.offset(Layouts.DEVICE_LIST, "usb_devices"));
    }

    @Test
    void loadsLibraryAndResolvesEveryFunction() {
        assertTrue(HackRf.libraryVersion().matches("\\d+\\.\\d+\\.\\d+"), HackRf.libraryVersion());
        assertFalse(HackRf.libraryRelease().isBlank());
        assertNotNull(Native.SET_FPGA_BITSTREAM);
    }

    @Test
    void decodesErrors() {
        assertEquals(HackRfError.NOT_FOUND, HackRfError.fromCode(-5));
        assertEquals(HackRfError.USB_API_VERSION, HackRfError.fromCode(-1005));
        assertEquals(HackRfError.UNKNOWN, HackRfError.fromCode(-12345));
        assertEquals("HackRF not found", HackRf.errorName(-5));
        assertEquals("hackrf_open failed: HACKRF_ERROR_NOT_FOUND (-5): HackRF not found",
            new HackRfException("hackrf_open", -5, HackRf.errorName(-5)).getMessage());
        assertEquals("hackrf_exit failed: HACKRF_ERROR_OTHER (-9999)",
            new HackRfException("hackrf_exit", -9999, null).getMessage());
    }

    @Test
    void namesBoards() {
        BoardId one = HackRf.boardId(BoardId.HACKRF1_OG);
        assertEquals("HackRF One", one.name());
        assertEquals(Set.of(Platform.HACKRF1_OG), one.platforms());
        assertEquals(Set.of(Platform.PRALINE), HackRf.boardId(BoardId.PRALINE).platforms());
        assertEquals(Set.of(), HackRf.boardId(0xFE).platforms());
        assertEquals("HackRF One", HackRf.usbBoardId(UsbBoardId.HACKRF_ONE).name());
        assertFalse(HackRf.boardRevision(BoardRevision.UNDETECTED).name().isBlank());
        assertTrue(new BoardRevision(0x84, "r9").isGreatScottGadgets());
        assertFalse(new BoardRevision(BoardRevision.UNDETECTED, "?").isGreatScottGadgets());
        assertFalse(HackRf.filterPathName(RfPathFilter.BYPASS).isBlank());
        assertEquals(Set.of(Platform.JAWBREAKER, Platform.RAD1O), Platform.fromMask(1 | 4 | 64));
    }

    @Test
    void computesFilterBandwidths() {
        assertEquals(1_750_000L, HackRf.computeBasebandFilterBandwidth(1_000_000L));
        assertEquals(10_000_000L, HackRf.computeBasebandFilterBandwidth(10_000_000L));
        assertEquals(9_000_000L, HackRf.computeBasebandFilterBandwidthBelow(10_000_000L));
        assertThrows(IllegalArgumentException.class, () -> HackRf.computeBasebandFilterBandwidth(-1));
    }

    @Test
    void enumeratesWithoutFailing() throws HackRfException {
        // CI machines may have no USB bus; libusb then reports no devices or fails to initialize.
        try {
            List<DeviceEntry> devices = HackRf.listDevices();
            for (DeviceEntry device : devices) {
                assertNotNull(device.usbBoardId());
            }
        } catch (HackRfException e) {
            assertEquals(HackRfError.LIBUSB, e.error(), e.getMessage());
        }
        HackRf.exit();
        HackRf.exit();
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> HackRf.open(-1));
        assertThrows(IllegalArgumentException.class, () -> HackRf.open(""));
        assertThrows(IllegalArgumentException.class, () -> HackRf.open("0".repeat(33)));
        assertThrows(IllegalArgumentException.class, () -> new OperacakeDwellTime(0, OperacakePort.PA1));
        assertThrows(IllegalArgumentException.class, () -> new OperacakeFrequencyRange(10, 5, OperacakePort.PA1));
    }

    @Test
    void copiesSamples() {
        try (Arena arena = Arena.ofConfined()) {
            byte[] iq = {64, -64, 127, -128};
            SampleBuffer buffer = new SampleBuffer(arena.allocateFrom(ValueLayout.JAVA_BYTE, iq));
            assertEquals(2, buffer.sampleCount());
            assertArrayEquals(iq, buffer.copyBytes());
            assertArrayEquals(new float[] {0.5f, -0.5f, 127 / 128f, -1f}, buffer.copyFloats());
            float[] target = new float[6];
            buffer.copyFloats(target);
            assertArrayEquals(new float[] {0.5f, -0.5f, 127 / 128f, -1f, 0, 0}, target);
            assertThrows(IndexOutOfBoundsException.class, () -> buffer.copyFloats(new float[3]));
        }
    }

    @Test
    void splitsSweepBlocks() {
        try (Arena arena = Arena.ofConfined()) {
            int size = SampleBuffer.SWEEP_BLOCK_SIZE;
            MemorySegment memory = arena.allocate(3L * size);
            ValueLayout.OfLong frequency = ValueLayout.JAVA_LONG_UNALIGNED.withOrder(ByteOrder.LITTLE_ENDIAN);
            for (int block : new int[] {0, 2}) {
                memory.set(ValueLayout.JAVA_BYTE, (long) block * size, (byte) 0x7F);
                memory.set(ValueLayout.JAVA_BYTE, (long) block * size + 1, (byte) 0x7F);
                memory.set(frequency, (long) block * size + 2, 2_400_000_000L + block);
                memory.set(ValueLayout.JAVA_BYTE, (long) block * size + 10, (byte) block);
            }
            List<SweepBlock> blocks = new SampleBuffer(memory).sweepBlocks();
            assertEquals(2, blocks.size());
            assertEquals(2_400_000_000L, blocks.get(0).frequency());
            assertEquals(2_400_000_002L, blocks.get(1).frequency());
            assertEquals(size - SampleBuffer.SWEEP_HEADER_SIZE, blocks.get(1).samples().byteSize());
            assertEquals(2, blocks.get(1).copyBytes()[0]);
        }
    }

    @Test
    void formatsUsbApiVersion() {
        assertEquals("1.09", HackRfDevice.formatUsbApiVersion(0x0109));
        assertEquals("1.10", HackRfDevice.formatUsbApiVersion(0x0110));
    }

    @Test
    void mapsPlatforms() {
        assertEquals("linux-x86_64", NativeLibraryLoader.platform("Linux", "amd64"));
        assertEquals("linux-aarch64", NativeLibraryLoader.platform("Linux", "aarch64"));
        assertEquals("macos-x86_64", NativeLibraryLoader.platform("Mac OS X", "x86_64"));
        assertEquals("macos-aarch64", NativeLibraryLoader.platform("Mac OS X", "aarch64"));
        assertEquals("windows-x86_64", NativeLibraryLoader.platform("Windows 11", "amd64"));
        assertEquals("windows-aarch64", NativeLibraryLoader.platform("Windows 11", "aarch64"));
        assertThrows(UnsatisfiedLinkError.class, () -> NativeLibraryLoader.platform("Solaris", "amd64"));
        assertThrows(UnsatisfiedLinkError.class, () -> NativeLibraryLoader.platform("Linux", "riscv64"));
    }
}
