package io.github.dsheirer.hackrf;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import io.github.dsheirer.hackrf.internal.Layouts;
import io.github.dsheirer.hackrf.internal.Native;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entry point of the library: finds and opens HackRF devices.
 *
 * <pre>{@code
 * try (HackRfDevice device = HackRf.open()) {
 *     device.setSampleRate(10_000_000);
 *     device.setFrequency(100_000_000L);
 *     device.setLnaGain(16);
 *     device.setVgaGain(20);
 *     device.startRx(buffer -> {
 *         process(buffer.copyBytes());
 *         return true;
 *     });
 *     Thread.sleep(1000);
 * }
 * }</pre>
 *
 * <p>The library is initialized on first use. The native enumeration, open and
 * close functions are not thread-safe, so they run one at a time.
 */
public final class HackRf {
    static final Object LOCK = new Object();

    // Set while a listener or transmit source runs. Every libhackrf call from
    // there would deadlock or corrupt the USB event loop, so they are refused.
    private static final ThreadLocal<Boolean> IN_CALLBACK = ThreadLocal.withInitial(() -> false);

    private static boolean initialized;

    private HackRf() {
    }

    /** Returns the version of the loaded native library, such as {@code 0.9.2}. */
    public static String libraryVersion() {
        return Native.invokeString(Native.LIBRARY_VERSION);
    }

    /** Returns the HackRF release the native library was built from, such as {@code 2026.01.3}. */
    public static String libraryRelease() {
        return Native.invokeString(Native.LIBRARY_RELEASE);
    }

    /**
     * Lists the connected devices. Devices that are in use are listed, but on
     * some systems without their serial number.
     */
    public static List<DeviceEntry> listDevices() throws HackRfException {
        rejectCallback();
        synchronized (LOCK) {
            initialize();
            MemorySegment list = Native.invokePointer(Native.DEVICE_LIST);
            if (list.equals(MemorySegment.NULL)) {
                throw new HackRfException("hackrf_device_list", HackRfError.NO_MEM.code(), null);
            }
            try {
                return readDeviceList(list.reinterpret(Layouts.DEVICE_LIST.byteSize()));
            } finally {
                Native.invokeVoid(Native.DEVICE_LIST_FREE, list);
            }
        }
    }

    private static List<DeviceEntry> readDeviceList(MemorySegment list) {
        int count = list.get(JAVA_INT, Layouts.offset(Layouts.DEVICE_LIST, "devicecount"));
        MemorySegment serials = list.get(ADDRESS, Layouts.offset(Layouts.DEVICE_LIST, "serial_numbers"))
            .reinterpret(count * ADDRESS.byteSize());
        MemorySegment boardIds = list.get(ADDRESS, Layouts.offset(Layouts.DEVICE_LIST, "usb_board_ids"))
            .reinterpret(count * JAVA_INT.byteSize());
        List<DeviceEntry> devices = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String serial = Native.string(serials.getAtIndex(ADDRESS, i));
            int sharing = Native.invoke(Native.DEVICE_LIST_BUS_SHARING, list, i);
            devices.add(new DeviceEntry(i, serial, usbBoardId(boardIds.getAtIndex(JAVA_INT, i)),
                sharing < 0 ? -1 : sharing));
        }
        return List.copyOf(devices);
    }

    /** Opens the first available device. */
    public static HackRfDevice open() throws HackRfException {
        return open("hackrf_open", Native.OPEN);
    }

    /**
     * Opens the device with a serial number from {@link #listDevices()}. A
     * serial number shorter than 32 digits matches the device whose serial
     * number ends with it.
     */
    public static HackRfDevice open(String serialNumber) throws HackRfException {
        Objects.requireNonNull(serialNumber, "serialNumber");
        // The native function opens the first device for an empty string and
        // finds nothing for one longer than 32 characters.
        if (serialNumber.isEmpty() || serialNumber.length() > 32) {
            throw new IllegalArgumentException("Serial number must have 1 to 32 characters: " + serialNumber);
        }
        try (Arena arena = Arena.ofConfined()) {
            return open("hackrf_open_by_serial", Native.OPEN_BY_SERIAL, arena.allocateFrom(serialNumber));
        }
    }

    /**
     * Opens a device by its index in a fresh device list. This also opens
     * devices whose serial number cannot be read. The order of the list is
     * stable only while no device is connected or disconnected.
     */
    public static HackRfDevice open(int index) throws HackRfException {
        if (index < 0) {
            throw new IllegalArgumentException("Invalid device index: " + index);
        }
        rejectCallback();
        synchronized (LOCK) {
            initialize();
            MemorySegment list = Native.invokePointer(Native.DEVICE_LIST);
            if (list.equals(MemorySegment.NULL)) {
                throw new HackRfException("hackrf_device_list", HackRfError.NO_MEM.code(), null);
            }
            try (Arena arena = Arena.ofConfined()) {
                int count = list.reinterpret(Layouts.DEVICE_LIST.byteSize())
                    .get(JAVA_INT, Layouts.offset(Layouts.DEVICE_LIST, "devicecount"));
                if (index >= count) {
                    throw new HackRfException("hackrf_device_list_open", HackRfError.NOT_FOUND.code(),
                        "no device at index " + index + "; " + count + " connected");
                }
                MemorySegment pointer = arena.allocate(ADDRESS);
                check("hackrf_device_list_open", Native.invoke(Native.DEVICE_LIST_OPEN, list, index, pointer));
                return new HackRfDevice(pointer.get(ADDRESS, 0));
            } finally {
                Native.invokeVoid(Native.DEVICE_LIST_FREE, list);
            }
        }
    }

    private static HackRfDevice open(String function, MethodHandle handle, Object... arguments)
            throws HackRfException {
        rejectCallback();
        synchronized (LOCK) {
            initialize();
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment pointer = arena.allocate(ADDRESS);
                Object[] all = new Object[arguments.length + 1];
                System.arraycopy(arguments, 0, all, 0, arguments.length);
                all[arguments.length] = pointer;
                check(function, Native.invoke(handle, all));
                return new HackRfDevice(pointer.get(ADDRESS, 0));
            }
        }
    }

    /**
     * Shuts the native library down, releasing libusb. It is initialized again
     * when needed, so calling this is optional; it is useful to release USB
     * resources in a long-running process.
     *
     * @throws HackRfException with {@link HackRfError#NOT_LAST_DEVICE} if a
     *     device is still open
     */
    public static void exit() throws HackRfException {
        rejectCallback();
        synchronized (LOCK) {
            if (initialized) {
                check("hackrf_exit", Native.invoke(Native.EXIT));
                initialized = false;
            }
        }
    }

    /**
     * Returns the baseband filter bandwidth the device supports that is
     * nearest to a bandwidth in Hz, for {@link HackRfDevice#setBasebandFilterBandwidth}.
     */
    public static long computeBasebandFilterBandwidth(long hertz) {
        return Integer.toUnsignedLong(Native.invoke(Native.COMPUTE_BASEBAND_FILTER_BW, u32(hertz)));
    }

    /**
     * Returns the highest baseband filter bandwidth the device supports that is
     * below a bandwidth in Hz, or the lowest bandwidth if none is.
     */
    public static long computeBasebandFilterBandwidthBelow(long hertz) {
        return Integer.toUnsignedLong(Native.invoke(Native.COMPUTE_BASEBAND_FILTER_BW_ROUND_DOWN_LT, u32(hertz)));
    }

    /** Returns the library's name for an RF path filter, such as "mixer bypass". */
    public static String filterPathName(RfPathFilter path) {
        return Native.invokeString(Native.FILTER_PATH_NAME, path.code());
    }

    /** Returns the library's description of an error code. */
    public static String errorName(int code) {
        return Native.invokeString(Native.ERROR_NAME, code);
    }

    private static void initialize() throws HackRfException {
        if (!initialized) {
            check("hackrf_init", Native.invoke(Native.INIT));
            initialized = true;
        }
    }

    static BoardId boardId(int code) {
        return new BoardId(code, Native.invokeString(Native.BOARD_ID_NAME, code),
            Platform.fromMask(Integer.toUnsignedLong(Native.invoke(Native.BOARD_ID_PLATFORM, code))));
    }

    static BoardRevision boardRevision(int code) {
        return new BoardRevision(code, Native.invokeString(Native.BOARD_REV_NAME, code));
    }

    static UsbBoardId usbBoardId(int code) {
        return new UsbBoardId(code, Native.invokeString(Native.USB_BOARD_ID_NAME, code));
    }

    static int check(String function, int result) throws HackRfException {
        if (result < 0) {
            throw new HackRfException(function, result, errorName(result));
        }
        return result;
    }

    static int u32(long value) {
        if (value < 0 || value > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("Value out of 32-bit range: " + value);
        }
        return (int) value;
    }

    /** Throws if the current thread is running a listener or transmit source. */
    static void rejectCallback() {
        if (IN_CALLBACK.get()) {
            throw new IllegalStateException("libhackrf cannot be called from a SampleListener or TransmitSource");
        }
    }

    /** Marks the current thread as running a callback; returns the previous state for {@link #exitCallback}. */
    static boolean enterCallback() {
        boolean previous = IN_CALLBACK.get();
        IN_CALLBACK.set(true);
        return previous;
    }

    static void exitCallback(boolean previous) {
        IN_CALLBACK.set(previous);
    }
}
