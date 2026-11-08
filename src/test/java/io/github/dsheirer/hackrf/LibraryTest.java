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
import io.github.dsheirer.libusb.NativeLoader;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.lang.foreign.ValueLayout;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
    void namesLibraries() {
        assertEquals("libhackrf.so", NativeLibraryLoader.libraryName("linux-aarch64"));
        assertEquals("libhackrf.dylib", NativeLibraryLoader.libraryName("macos-x86_64"));
        assertEquals("hackrf.dll", NativeLibraryLoader.libraryName("windows-aarch64"));
    }

    @Test
    void sharesLibusbWithJlibusb() throws Throwable {
        // Loading libhackrf loads jlibusb's libusb, so both sit in its directory.
        assertFalse(HackRf.libraryVersion().isBlank());
        Path directory = NativeLoader.directory();
        String platform = NativeLoader.platform();
        assertTrue(Files.isRegularFile(directory.resolve(NativeLoader.libusbName(platform))));
        assertTrue(Files.isRegularFile(directory.resolve(NativeLibraryLoader.libraryName(platform))));
        if (platform.startsWith("linux")) {
            // One copy of libusb in the process, the one jlibusb extracted.
            List<String> mapped = Files.readAllLines(Path.of("/proc/self/maps")).stream()
                .filter(line -> line.contains("libusb-1.0"))
                .map(line -> line.substring(line.indexOf('/')))
                .distinct()
                .toList();
            assertEquals(List.of(directory.resolve("libusb-1.0.so.0").toString()), mapped);
        } else if (platform.startsWith("macos")) {
            // dyld must reuse the libusb jlibusb loaded when libhackrf asks for
            // @loader_path/libusb-1.0.0.dylib, even through the /var symlink.
            List<String> images = dyldImages().stream().filter(name -> name.contains("libusb-1.0")).toList();
            assertEquals(1, images.size(), images.toString());
        }
    }

    /** Paths of the images dyld has loaded into this process. */
    private static List<String> dyldImages() throws Throwable {
        Linker linker = Linker.nativeLinker();
        SymbolLookup system = linker.defaultLookup();
        MethodHandle count = linker.downcallHandle(system.find("_dyld_image_count").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT));
        MethodHandle name = linker.downcallHandle(system.find("_dyld_get_image_name").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
        int images = (int) count.invokeExact();
        List<String> names = new ArrayList<>();
        for (int i = 0; i < images; i++) {
            MemorySegment pointer = (MemorySegment) name.invokeExact(i);
            if (!pointer.equals(MemorySegment.NULL)) {
                names.add(pointer.reinterpret(Long.MAX_VALUE).getString(0));
            }
        }
        return names;
    }
}
