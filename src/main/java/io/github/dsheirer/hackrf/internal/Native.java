package io.github.dsheirer.hackrf.internal;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_DOUBLE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;

/**
 * Downcall handles for the public functions of libhackrf.
 *
 * <p>C enums are passed as {@code int}, {@code uint8_t} as {@code byte},
 * {@code uint16_t} as {@code short}, {@code uint32_t} as {@code int} and
 * {@code uint64_t} and {@code size_t} as {@code long}: every supported platform
 * is 64-bit.
 *
 * <p>{@code native/check-bindings.sh} compares this list against {@code hackrf.h}.
 */
public final class Native {
    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup LOOKUP = NativeLibraryLoader.load();

    // ---- Library ----
    public static final MethodHandle INIT = call("hackrf_init");
    public static final MethodHandle EXIT = call("hackrf_exit");
    public static final MethodHandle LIBRARY_VERSION = pointerCall("hackrf_library_version");
    public static final MethodHandle LIBRARY_RELEASE = pointerCall("hackrf_library_release");
    public static final MethodHandle ERROR_NAME = pointerCall("hackrf_error_name", JAVA_INT);
    public static final MethodHandle BOARD_ID_NAME = pointerCall("hackrf_board_id_name", JAVA_INT);
    public static final MethodHandle BOARD_ID_PLATFORM = call("hackrf_board_id_platform", JAVA_INT);
    public static final MethodHandle USB_BOARD_ID_NAME = pointerCall("hackrf_usb_board_id_name", JAVA_INT);
    public static final MethodHandle BOARD_REV_NAME = pointerCall("hackrf_board_rev_name", JAVA_INT);
    public static final MethodHandle FILTER_PATH_NAME = pointerCall("hackrf_filter_path_name", JAVA_INT);
    public static final MethodHandle COMPUTE_BASEBAND_FILTER_BW =
        call("hackrf_compute_baseband_filter_bw", JAVA_INT);
    public static final MethodHandle COMPUTE_BASEBAND_FILTER_BW_ROUND_DOWN_LT =
        call("hackrf_compute_baseband_filter_bw_round_down_lt", JAVA_INT);

    // ---- Devices ----
    public static final MethodHandle DEVICE_LIST = pointerCall("hackrf_device_list");
    public static final MethodHandle DEVICE_LIST_OPEN =
        call("hackrf_device_list_open", ADDRESS, JAVA_INT, ADDRESS);
    public static final MethodHandle DEVICE_LIST_BUS_SHARING =
        call("hackrf_device_list_bus_sharing", ADDRESS, JAVA_INT);
    public static final MethodHandle DEVICE_LIST_FREE = voidCall("hackrf_device_list_free", ADDRESS);
    public static final MethodHandle OPEN = call("hackrf_open", ADDRESS);
    public static final MethodHandle OPEN_BY_SERIAL = call("hackrf_open_by_serial", ADDRESS, ADDRESS);
    public static final MethodHandle CLOSE = call("hackrf_close", ADDRESS);
    public static final MethodHandle RESET = call("hackrf_reset", ADDRESS);

    // ---- Streaming ----
    public static final MethodHandle START_RX = call("hackrf_start_rx", ADDRESS, ADDRESS, ADDRESS);
    public static final MethodHandle START_RX_SWEEP = call("hackrf_start_rx_sweep", ADDRESS, ADDRESS, ADDRESS);
    public static final MethodHandle STOP_RX = call("hackrf_stop_rx", ADDRESS);
    public static final MethodHandle START_TX = call("hackrf_start_tx", ADDRESS, ADDRESS, ADDRESS);
    public static final MethodHandle SET_TX_BLOCK_COMPLETE_CALLBACK =
        call("hackrf_set_tx_block_complete_callback", ADDRESS, ADDRESS);
    public static final MethodHandle ENABLE_TX_FLUSH = call("hackrf_enable_tx_flush", ADDRESS, ADDRESS, ADDRESS);
    public static final MethodHandle STOP_TX = call("hackrf_stop_tx", ADDRESS);
    public static final MethodHandle IS_STREAMING = call("hackrf_is_streaming", ADDRESS);
    public static final MethodHandle SET_TX_UNDERRUN_LIMIT = call("hackrf_set_tx_underrun_limit", ADDRESS, JAVA_INT);
    public static final MethodHandle SET_RX_OVERRUN_LIMIT = call("hackrf_set_rx_overrun_limit", ADDRESS, JAVA_INT);
    public static final MethodHandle SET_HW_SYNC_MODE = call("hackrf_set_hw_sync_mode", ADDRESS, JAVA_BYTE);
    public static final MethodHandle INIT_SWEEP = call("hackrf_init_sweep",
        ADDRESS, ADDRESS, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT, JAVA_INT);
    public static final MethodHandle GET_TRANSFER_BUFFER_SIZE =
        LINKER.downcallHandle(symbol("hackrf_get_transfer_buffer_size"), FunctionDescriptor.of(JAVA_LONG, ADDRESS));
    public static final MethodHandle GET_TRANSFER_QUEUE_DEPTH = call("hackrf_get_transfer_queue_depth", ADDRESS);

    // ---- Device information ----
    public static final MethodHandle BOARD_ID_READ = call("hackrf_board_id_read", ADDRESS, ADDRESS);
    public static final MethodHandle BOARD_REV_READ = call("hackrf_board_rev_read", ADDRESS, ADDRESS);
    public static final MethodHandle SUPPORTED_PLATFORM_READ = call("hackrf_supported_platform_read", ADDRESS, ADDRESS);
    public static final MethodHandle VERSION_STRING_READ =
        call("hackrf_version_string_read", ADDRESS, ADDRESS, JAVA_BYTE);
    public static final MethodHandle USB_API_VERSION_READ = call("hackrf_usb_api_version_read", ADDRESS, ADDRESS);
    public static final MethodHandle BOARD_PARTID_SERIALNO_READ =
        call("hackrf_board_partid_serialno_read", ADDRESS, ADDRESS);

    // ---- Tuning and gain ----
    public static final MethodHandle SET_FREQ = call("hackrf_set_freq", ADDRESS, JAVA_LONG);
    public static final MethodHandle SET_FREQ_EXPLICIT =
        call("hackrf_set_freq_explicit", ADDRESS, JAVA_LONG, JAVA_LONG, JAVA_INT);
    public static final MethodHandle SET_SAMPLE_RATE_MANUAL =
        call("hackrf_set_sample_rate_manual", ADDRESS, JAVA_INT, JAVA_INT);
    public static final MethodHandle SET_SAMPLE_RATE = call("hackrf_set_sample_rate", ADDRESS, JAVA_DOUBLE);
    public static final MethodHandle SET_BASEBAND_FILTER_BANDWIDTH =
        call("hackrf_set_baseband_filter_bandwidth", ADDRESS, JAVA_INT);
    public static final MethodHandle SET_AMP_ENABLE = call("hackrf_set_amp_enable", ADDRESS, JAVA_BYTE);
    public static final MethodHandle SET_LNA_GAIN = call("hackrf_set_lna_gain", ADDRESS, JAVA_INT);
    public static final MethodHandle SET_VGA_GAIN = call("hackrf_set_vga_gain", ADDRESS, JAVA_INT);
    public static final MethodHandle SET_TXVGA_GAIN = call("hackrf_set_txvga_gain", ADDRESS, JAVA_INT);
    public static final MethodHandle SET_ANTENNA_ENABLE = call("hackrf_set_antenna_enable", ADDRESS, JAVA_BYTE);
    public static final MethodHandle SET_USER_BIAS_T_OPTS = call("hackrf_set_user_bias_t_opts", ADDRESS, ADDRESS);

    // ---- Clocks, connectors and user interface ----
    public static final MethodHandle SET_CLKOUT_ENABLE = call("hackrf_set_clkout_enable", ADDRESS, JAVA_BYTE);
    public static final MethodHandle GET_CLKIN_STATUS = call("hackrf_get_clkin_status", ADDRESS, ADDRESS);
    public static final MethodHandle SET_UI_ENABLE = call("hackrf_set_ui_enable", ADDRESS, JAVA_BYTE);
    public static final MethodHandle SET_LEDS = call("hackrf_set_leds", ADDRESS, JAVA_BYTE);
    public static final MethodHandle SET_P1_CTRL = call("hackrf_set_p1_ctrl", ADDRESS, JAVA_INT);
    public static final MethodHandle SET_P2_CTRL = call("hackrf_set_p2_ctrl", ADDRESS, JAVA_INT);
    public static final MethodHandle SET_CLKIN_CTRL = call("hackrf_set_clkin_ctrl", ADDRESS, JAVA_INT);
    public static final MethodHandle SET_NARROWBAND_FILTER = call("hackrf_set_narrowband_filter", ADDRESS, JAVA_BYTE);
    public static final MethodHandle SET_FPGA_BITSTREAM = call("hackrf_set_fpga_bitstream", ADDRESS, JAVA_BYTE);

    // ---- Opera Cake ----
    public static final MethodHandle GET_OPERACAKE_BOARDS = call("hackrf_get_operacake_boards", ADDRESS, ADDRESS);
    public static final MethodHandle SET_OPERACAKE_MODE =
        call("hackrf_set_operacake_mode", ADDRESS, JAVA_BYTE, JAVA_INT);
    public static final MethodHandle GET_OPERACAKE_MODE =
        call("hackrf_get_operacake_mode", ADDRESS, JAVA_BYTE, ADDRESS);
    public static final MethodHandle SET_OPERACAKE_PORTS =
        call("hackrf_set_operacake_ports", ADDRESS, JAVA_BYTE, JAVA_BYTE, JAVA_BYTE);
    public static final MethodHandle SET_OPERACAKE_DWELL_TIMES =
        call("hackrf_set_operacake_dwell_times", ADDRESS, ADDRESS, JAVA_BYTE);
    public static final MethodHandle SET_OPERACAKE_FREQ_RANGES =
        call("hackrf_set_operacake_freq_ranges", ADDRESS, ADDRESS, JAVA_BYTE);
    // Deprecated upstream in favour of hackrf_set_operacake_freq_ranges.
    public static final MethodHandle SET_OPERACAKE_RANGES =
        call("hackrf_set_operacake_ranges", ADDRESS, ADDRESS, JAVA_BYTE);
    public static final MethodHandle OPERACAKE_GPIO_TEST =
        call("hackrf_operacake_gpio_test", ADDRESS, JAVA_BYTE, ADDRESS);

    // ---- Diagnostics ----
    public static final MethodHandle GET_M0_STATE = call("hackrf_get_m0_state", ADDRESS, ADDRESS);
    public static final MethodHandle READ_SELFTEST = call("hackrf_read_selftest", ADDRESS, ADDRESS);
    public static final MethodHandle TEST_RTC_OSC = call("hackrf_test_rtc_osc", ADDRESS, ADDRESS);
    public static final MethodHandle READ_ADC = call("hackrf_read_adc", ADDRESS, JAVA_BYTE, ADDRESS);

    // ---- Registers ----
    public static final MethodHandle MAX2837_READ = call("hackrf_max2837_read", ADDRESS, JAVA_BYTE, ADDRESS);
    public static final MethodHandle MAX2837_WRITE = call("hackrf_max2837_write", ADDRESS, JAVA_BYTE, JAVA_SHORT);
    public static final MethodHandle MAX2831_READ = call("hackrf_max2831_read", ADDRESS, JAVA_BYTE, ADDRESS);
    public static final MethodHandle MAX2831_WRITE = call("hackrf_max2831_write", ADDRESS, JAVA_BYTE, JAVA_SHORT);
    public static final MethodHandle SI5351C_READ = call("hackrf_si5351c_read", ADDRESS, JAVA_SHORT, ADDRESS);
    public static final MethodHandle SI5351C_WRITE = call("hackrf_si5351c_write", ADDRESS, JAVA_SHORT, JAVA_SHORT);
    public static final MethodHandle RFFC5071_READ = call("hackrf_rffc5071_read", ADDRESS, JAVA_BYTE, ADDRESS);
    public static final MethodHandle RFFC5071_WRITE = call("hackrf_rffc5071_write", ADDRESS, JAVA_BYTE, JAVA_SHORT);
    public static final MethodHandle FPGA_READ_REGISTER =
        call("hackrf_fpga_read_register", ADDRESS, JAVA_BYTE, ADDRESS);
    public static final MethodHandle FPGA_WRITE_REGISTER =
        call("hackrf_fpga_write_register", ADDRESS, JAVA_BYTE, JAVA_BYTE);

    // ---- Firmware flash and CPLD ----
    public static final MethodHandle SPIFLASH_ERASE = call("hackrf_spiflash_erase", ADDRESS);
    public static final MethodHandle SPIFLASH_WRITE =
        call("hackrf_spiflash_write", ADDRESS, JAVA_INT, JAVA_SHORT, ADDRESS);
    public static final MethodHandle SPIFLASH_READ =
        call("hackrf_spiflash_read", ADDRESS, JAVA_INT, JAVA_SHORT, ADDRESS);
    public static final MethodHandle SPIFLASH_STATUS = call("hackrf_spiflash_status", ADDRESS, ADDRESS);
    public static final MethodHandle SPIFLASH_CLEAR_STATUS = call("hackrf_spiflash_clear_status", ADDRESS);
    public static final MethodHandle CPLD_WRITE = call("hackrf_cpld_write", ADDRESS, ADDRESS, JAVA_INT);

    private Native() {
    }

    private static MemorySegment symbol(String name) {
        return LOOKUP.find(name).orElseThrow(() -> new UnsatisfiedLinkError("libhackrf has no symbol " + name));
    }

    private static MethodHandle call(String name, MemoryLayout... arguments) {
        return LINKER.downcallHandle(symbol(name), FunctionDescriptor.of(JAVA_INT, arguments));
    }

    private static MethodHandle pointerCall(String name, MemoryLayout... arguments) {
        return LINKER.downcallHandle(symbol(name), FunctionDescriptor.of(ADDRESS, arguments));
    }

    private static MethodHandle voidCall(String name, MemoryLayout... arguments) {
        return LINKER.downcallHandle(symbol(name), FunctionDescriptor.ofVoid(arguments));
    }

    /**
     * Calls a native function returning {@code int}. This boxes its arguments,
     * which is fine for the control paths using it; samples arrive by upcall.
     */
    public static int invoke(MethodHandle function, Object... arguments) {
        return (int) invokeObject(function, arguments);
    }

    /** Calls a native function returning {@code size_t} or {@code uint64_t}. */
    public static long invokeLong(MethodHandle function, Object... arguments) {
        return (long) invokeObject(function, arguments);
    }

    /** Calls a native function returning a pointer. */
    public static MemorySegment invokePointer(MethodHandle function, Object... arguments) {
        return (MemorySegment) invokeObject(function, arguments);
    }

    /**
     * Calls a native function returning {@code const char*} and reads the
     * string. Returns null for a null pointer.
     */
    public static String invokeString(MethodHandle function, Object... arguments) {
        return string(invokePointer(function, arguments));
    }

    /** Calls a native function that returns nothing. */
    public static void invokeVoid(MethodHandle function, Object... arguments) {
        invokeObject(function, arguments);
    }

    /** Reads a NUL-terminated string from a pointer, or returns null for a null pointer. */
    public static String string(MemorySegment pointer) {
        if (pointer.equals(MemorySegment.NULL)) {
            return null;
        }
        return pointer.reinterpret(Integer.MAX_VALUE).getString(0);
    }

    private static Object invokeObject(MethodHandle function, Object... arguments) {
        try {
            return function.invokeWithArguments(arguments);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new IllegalStateException("Native call failed", t);
        }
    }

    /** Creates an upcall stub for {@code hackrf_sample_block_cb_fn}. */
    public static MemorySegment sampleBlockCallback(MethodHandle target, Arena arena) {
        return LINKER.upcallStub(target, FunctionDescriptor.of(JAVA_INT, ADDRESS), arena);
    }

    /** Creates an upcall stub for {@code hackrf_tx_block_complete_cb_fn}. */
    public static MemorySegment txBlockCompleteCallback(MethodHandle target, Arena arena) {
        return LINKER.upcallStub(target, FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT), arena);
    }

    /** Creates an upcall stub for {@code hackrf_flush_cb_fn}. */
    public static MemorySegment flushCallback(MethodHandle target, Arena arena) {
        return LINKER.upcallStub(target, FunctionDescriptor.ofVoid(ADDRESS, JAVA_INT), arena);
    }
}
