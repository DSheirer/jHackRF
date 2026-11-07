package io.github.dsheirer.hackrf;

import static io.github.dsheirer.hackrf.HackRf.check;
import static io.github.dsheirer.hackrf.HackRf.u32;
import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_SHORT;

import io.github.dsheirer.hackrf.internal.Layouts;
import io.github.dsheirer.hackrf.internal.Native;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * An open HackRF device: HackRF One, HackRF Pro, rad1o or Jawbreaker. Close it
 * when finished, which also stops streaming.
 *
 * <p>Samples are interleaved signed 8-bit I/Q in both directions. Set the
 * sample rate, frequency and gains before starting a stream: the device keeps
 * its previous values, so their state is otherwise unknown.
 *
 * <p>Control methods may be called from any thread, but not from a
 * {@link SampleListener} or {@link TransmitSource}, where they throw
 * {@link IllegalStateException}. Do not close a device while other threads are
 * still using it.
 *
 * <p>Methods documented as needing a USB API version throw a
 * {@link HackRfException} with {@link HackRfError#USB_API_VERSION} on older
 * firmware; see {@link #usbApiVersion()}.
 */
public final class HackRfDevice implements AutoCloseable {
    /** Size of the buffer {@code hackrf_version_string_read} fills, including the terminator. */
    private static final int VERSION_STRING_SIZE = 256;

    private static final MethodHandle ON_SAMPLE_BLOCK;
    private static final MethodHandle ON_TX_BLOCK_COMPLETE;
    private static final MethodHandle ON_FLUSH;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            ON_SAMPLE_BLOCK = lookup.findVirtual(HackRfDevice.class, "onSampleBlock",
                MethodType.methodType(int.class, MemorySegment.class));
            ON_TX_BLOCK_COMPLETE = lookup.findVirtual(HackRfDevice.class, "onTxBlockComplete",
                MethodType.methodType(void.class, MemorySegment.class, int.class));
            ON_FLUSH = lookup.findVirtual(HackRfDevice.class, "onFlush",
                MethodType.methodType(void.class, MemorySegment.class, int.class));
        } catch (ReflectiveOperationException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private enum Mode { NONE, RX, SWEEP, TX }

    private final MemorySegment handle;
    private final Object lifecycleLock = new Object();
    private boolean closed;
    private Mode mode = Mode.NONE;
    private boolean flushEnabled;

    // The upcall stubs live as long as this object, not as long as one stream,
    // so a native thread that outlives a stream never calls freed code.
    private final MemorySegment sampleBlockCallback;
    private final MemorySegment txBlockCompleteCallback;
    private final MemorySegment flushCallback;

    // Streaming state: written under lifecycleLock before the stream starts,
    // read by the callback thread.
    private volatile SampleListener listener;
    private volatile TransmitSource source;
    private volatile Throwable streamingFailure;
    private volatile CountDownLatch transmitComplete;
    private volatile boolean transmitFlushed;

    HackRfDevice(MemorySegment handle) {
        this.handle = handle;
        Arena arena = Arena.ofAuto();
        this.sampleBlockCallback = Native.sampleBlockCallback(ON_SAMPLE_BLOCK.bindTo(this), arena);
        this.txBlockCompleteCallback = Native.txBlockCompleteCallback(ON_TX_BLOCK_COMPLETE.bindTo(this), arena);
        this.flushCallback = Native.flushCallback(ON_FLUSH.bindTo(this), arena);
    }

    // ---- Information ----

    /** Reads the board type from the device. */
    public BoardId boardId() throws HackRfException {
        return HackRf.boardId(readU8("hackrf_board_id_read", Native.BOARD_ID_READ));
    }

    /** Reads the hardware revision from the device. Requires USB API version 0x0106. */
    public BoardRevision boardRevision() throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(JAVA_BYTE);
            value.set(JAVA_BYTE, 0, (byte) BoardRevision.UNDETECTED);
            check("hackrf_board_rev_read", Native.invoke(Native.BOARD_REV_READ, handle(), value));
            return HackRf.boardRevision(Byte.toUnsignedInt(value.get(JAVA_BYTE, 0)));
        }
    }

    /**
     * Reads the hardware platforms the device's firmware supports. Requires
     * USB API version 0x0106.
     */
    public Set<Platform> supportedPlatforms() throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(JAVA_INT);
            check("hackrf_supported_platform_read", Native.invoke(Native.SUPPORTED_PLATFORM_READ, handle(), value));
            return Platform.fromMask(Integer.toUnsignedLong(value.get(JAVA_INT, 0)));
        }
    }

    /** Reads the firmware version string, such as {@code 2026.01.3}. */
    public String firmwareVersion() throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment version = arena.allocate(VERSION_STRING_SIZE);
            // The length excludes the terminator, which the library appends.
            check("hackrf_version_string_read", Native.invoke(Native.VERSION_STRING_READ, handle(), version,
                (byte) (VERSION_STRING_SIZE - 1)));
            return version.getString(0);
        }
    }

    /**
     * Returns the USB API version of the firmware as a binary-coded number:
     * {@code 0x0109} is version 1.09. Some functions need a minimum version.
     */
    public int usbApiVersion() throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(JAVA_SHORT);
            check("hackrf_usb_api_version_read", Native.invoke(Native.USB_API_VERSION_READ, handle(), value));
            return Short.toUnsignedInt(value.get(JAVA_SHORT, 0));
        }
    }

    /** Formats a USB API version as upstream's tools do, such as {@code 1.09}. */
    public static String formatUsbApiVersion(int version) {
        return String.format(Locale.ROOT, "%x.%02x", (version >> 8) & 0xFF, version & 0xFF);
    }

    /** Reads the microcontroller part ID and the serial number. */
    public BoardInfo boardInfo() throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment info = arena.allocate(Layouts.PART_SERIAL);
            check("hackrf_board_partid_serialno_read",
                Native.invoke(Native.BOARD_PARTID_SERIALNO_READ, handle(), info));
            long partOffset = Layouts.offset(Layouts.PART_SERIAL, "part_id");
            long serialOffset = Layouts.offset(Layouts.PART_SERIAL, "serial_no");
            StringBuilder serial = new StringBuilder(32);
            for (int i = 0; i < 4; i++) {
                serial.append(String.format(Locale.ROOT, "%08x", info.get(JAVA_INT, serialOffset + 4L * i)));
            }
            return new BoardInfo(Integer.toUnsignedLong(info.get(JAVA_INT, partOffset)),
                Integer.toUnsignedLong(info.get(JAVA_INT, partOffset + 4)), serial.toString());
        }
    }

    /** Returns the size in bytes of each buffer passed to a listener or transmit source. */
    public int transferBufferSize() {
        return Math.toIntExact(Native.invokeLong(Native.GET_TRANSFER_BUFFER_SIZE, handle()));
    }

    /** Returns the number of transfer buffers the library keeps in flight. */
    public int transferQueueDepth() {
        return Native.invoke(Native.GET_TRANSFER_QUEUE_DEPTH, handle());
    }

    // ---- Tuning ----

    /**
     * Tunes to a center frequency in Hz. The HackRF One covers 1 MHz to 6 GHz;
     * 0 to 7.25 GHz is accepted.
     */
    public void setFrequency(long hertz) throws HackRfException {
        if (hertz < 0) {
            throw new IllegalArgumentException("Frequency out of range: " + hertz);
        }
        check("hackrf_set_freq", Native.invoke(Native.SET_FREQ, handle(), hertz));
    }

    /**
     * Tunes by setting the intermediate frequency, the local oscillator and the
     * filter path directly. The center frequency is IF - LO for
     * {@link RfPathFilter#LOW_PASS}, IF + LO for {@link RfPathFilter#HIGH_PASS}
     * and IF for {@link RfPathFilter#BYPASS}.
     *
     * @param ifHertz intermediate frequency, 2 to 3 GHz (2.15 to 2.75 GHz recommended)
     * @param loHertz local oscillator frequency, 84.375 MHz to 5.4 GHz; ignored for bypass
     * @param path filter path
     */
    public void setFrequencyExplicit(long ifHertz, long loHertz, RfPathFilter path) throws HackRfException {
        Objects.requireNonNull(path, "path");
        if (ifHertz < 0 || loHertz < 0) {
            throw new IllegalArgumentException("Frequency out of range: IF " + ifHertz + ", LO " + loHertz);
        }
        check("hackrf_set_freq_explicit",
            Native.invoke(Native.SET_FREQ_EXPLICIT, handle(), ifHertz, loHertz, path.code()));
    }

    /**
     * Sets the sample rate in Hz. 2 to 20 MHz is supported, and the default is
     * 10 MHz. This also sets the baseband filter to its default for the rate,
     * so set the filter afterwards.
     */
    public void setSampleRate(double hertz) throws HackRfException {
        if (!(hertz > 0) || Double.isInfinite(hertz)) {
            throw new IllegalArgumentException("Sample rate out of range: " + hertz);
        }
        check("hackrf_set_sample_rate", Native.invoke(Native.SET_SAMPLE_RATE, handle(), hertz));
    }

    /**
     * Sets the sample rate to {@code hertz / divider}. This also sets the
     * baseband filter to its default for the rate, so set the filter afterwards.
     *
     * @param divider 1 to 31
     */
    public void setSampleRateManual(long hertz, int divider) throws HackRfException {
        if (divider < 1 || divider > 31) {
            throw new IllegalArgumentException("Divider out of range 1 to 31: " + divider);
        }
        check("hackrf_set_sample_rate_manual",
            Native.invoke(Native.SET_SAMPLE_RATE_MANUAL, handle(), u32(hertz), divider));
    }

    /**
     * Sets the baseband filter bandwidth in Hz: one of 1.75, 2.5, 3.5, 5, 5.5,
     * 6, 7, 8, 9, 10, 12, 14, 15, 20, 24 or 28 MHz. Other values select the
     * nearest supported bandwidth below; see
     * {@link HackRf#computeBasebandFilterBandwidth(long)}.
     */
    public void setBasebandFilterBandwidth(long hertz) throws HackRfException {
        check("hackrf_set_baseband_filter_bandwidth",
            Native.invoke(Native.SET_BASEBAND_FILTER_BANDWIDTH, handle(), u32(hertz)));
    }

    // ---- Gain ----

    /** Enables or disables the RF amplifier, about 11 dB, for both receive and transmit. */
    public void setAmpEnable(boolean enabled) throws HackRfException {
        check("hackrf_set_amp_enable", Native.invoke(Native.SET_AMP_ENABLE, handle(), flag(enabled)));
    }

    /** Sets the receive LNA (IF) gain, 0 to 40 dB in 8 dB steps; other values round down. */
    public void setLnaGain(int decibels) throws HackRfException {
        check("hackrf_set_lna_gain", Native.invoke(Native.SET_LNA_GAIN, handle(), u32(decibels)));
    }

    /** Sets the receive VGA (baseband) gain, 0 to 62 dB in 2 dB steps; other values round down. */
    public void setVgaGain(int decibels) throws HackRfException {
        check("hackrf_set_vga_gain", Native.invoke(Native.SET_VGA_GAIN, handle(), u32(decibels)));
    }

    /** Sets the transmit VGA (IF) gain, 0 to 47 dB in 1 dB steps. */
    public void setTxVgaGain(int decibels) throws HackRfException {
        check("hackrf_set_txvga_gain", Native.invoke(Native.SET_TXVGA_GAIN, handle(), u32(decibels)));
    }

    // ---- Bias tee ----

    /**
     * Turns the antenna port power (bias tee, 3.3 V, at most 50 mA) on or off.
     * This puts DC power on the antenna connector, which can damage equipment
     * not built for it. The firmware turns it off when the device returns to
     * idle, unless {@link #setUserBiasTeeOptions} says otherwise.
     */
    public void setAntennaEnable(boolean enabled) throws HackRfException {
        check("hackrf_set_antenna_enable", Native.invoke(Native.SET_ANTENNA_ENABLE, handle(), flag(enabled)));
    }

    /**
     * Sets what the device does to the bias tee when it enters each mode. By
     * default it turns the bias tee off when going idle. Requires USB API
     * version 0x0108.
     */
    public void setUserBiasTeeOptions(BiasTeeAction off, BiasTeeAction rx, BiasTeeAction tx)
            throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment request = arena.allocate(Layouts.BIAS_T_USER_SETTING_REQ);
            setBiasTee(request, "off", off);
            setBiasTee(request, "rx", rx);
            setBiasTee(request, "tx", tx);
            check("hackrf_set_user_bias_t_opts", Native.invoke(Native.SET_USER_BIAS_T_OPTS, handle(), request));
        }
    }

    private static void setBiasTee(MemorySegment request, String mode, BiasTeeAction action) {
        Objects.requireNonNull(action, mode);
        setBool(request, mode, "do_update", action != BiasTeeAction.UNCHANGED);
        setBool(request, mode, "change_on_mode_entry",
            action == BiasTeeAction.ENABLE || action == BiasTeeAction.DISABLE);
        setBool(request, mode, "enabled", action == BiasTeeAction.ENABLE);
    }

    private static void setBool(MemorySegment request, String mode, String field, boolean value) {
        request.set(JAVA_BYTE, Layouts.offset(Layouts.BIAS_T_USER_SETTING_REQ, mode, field), flag(value));
    }

    // ---- Clocks, synchronization and connectors ----

    /** Enables or disables the clock output on CLKOUT. Requires USB API version 0x0103. */
    public void setClkoutEnable(boolean enabled) throws HackRfException {
        check("hackrf_set_clkout_enable", Native.invoke(Native.SET_CLKOUT_ENABLE, handle(), flag(enabled)));
    }

    /**
     * Returns true if the device detects an external clock on CLKIN. Requires
     * USB API version 0x0106.
     */
    public boolean isClkinDetected() throws HackRfException {
        return readU8("hackrf_get_clkin_status", Native.GET_CLKIN_STATUS) != 0;
    }

    /**
     * Enables or disables hardware triggering: when enabled, streaming waits
     * for a trigger on the trigger input. Requires USB API version 0x0102.
     */
    public void setHwSyncMode(boolean enabled) throws HackRfException {
        check("hackrf_set_hw_sync_mode", Native.invoke(Native.SET_HW_SYNC_MODE, handle(), flag(enabled)));
    }

    /** Selects the signal on the P1 SMA connector of a HackRF Pro. */
    public void setP1Signal(P1Signal signal) throws HackRfException {
        check("hackrf_set_p1_ctrl", Native.invoke(Native.SET_P1_CTRL, handle(), signal.code()));
    }

    /** Selects the signal on the P2 SMA connector of a HackRF Pro. */
    public void setP2Signal(P2Signal signal) throws HackRfException {
        check("hackrf_set_p2_ctrl", Native.invoke(Native.SET_P2_CTRL, handle(), signal.code()));
    }

    /** Selects the source of the clock input of a HackRF Pro. */
    public void setClkinSignal(ClkinSignal signal) throws HackRfException {
        check("hackrf_set_clkin_ctrl", Native.invoke(Native.SET_CLKIN_CTRL, handle(), signal.code()));
    }

    /** Enables or disables the narrowband filter of a HackRF Pro. */
    public void setNarrowbandFilter(boolean enabled) throws HackRfException {
        check("hackrf_set_narrowband_filter",
            Native.invoke(Native.SET_NARROWBAND_FILTER, handle(), flag(enabled)));
    }

    /** Loads one of the FPGA bitstreams stored in a HackRF Pro, by index. */
    public void setFpgaBitstream(int index) throws HackRfException {
        check("hackrf_set_fpga_bitstream", Native.invoke(Native.SET_FPGA_BITSTREAM, handle(), u8(index)));
    }

    // ---- User interface ----

    /**
     * Enables or disables the display of a PortaPack or rad1o. Requires USB API
     * version 0x0104.
     */
    public void setUiEnable(boolean enabled) throws HackRfException {
        check("hackrf_set_ui_enable", Native.invoke(Native.SET_UI_ENABLE, handle(), flag(enabled)));
    }

    /**
     * Turns the LEDs on or off, one bit per LED: on a HackRF One, bit 0 is USB,
     * bit 1 RX and bit 2 TX. The firmware may change them again. Requires USB
     * API version 0x0107.
     */
    public void setLeds(int state) throws HackRfException {
        check("hackrf_set_leds", Native.invoke(Native.SET_LEDS, handle(), u8(state)));
    }

    /**
     * Resets the device, which disconnects it from USB. Close this object
     * afterwards and open the device again. Requires USB API version 0x0102.
     */
    public void reset() throws HackRfException {
        check("hackrf_reset", Native.invoke(Native.RESET, handle()));
    }

    // ---- Streaming ----

    /**
     * Sets how many bytes of transmit underrun the device tolerates before it
     * stops by itself, which guards against a crashed host; 0, the default,
     * means no limit. Requires USB API version 0x0106.
     */
    public void setTxUnderrunLimit(long bytes) throws HackRfException {
        check("hackrf_set_tx_underrun_limit", Native.invoke(Native.SET_TX_UNDERRUN_LIMIT, handle(), u32(bytes)));
    }

    /**
     * Sets how many bytes of receive overrun the device tolerates before it
     * stops by itself; 0, the default, means no limit. Requires USB API version
     * 0x0106.
     */
    public void setRxOverrunLimit(long bytes) throws HackRfException {
        check("hackrf_set_rx_overrun_limit", Native.invoke(Native.SET_RX_OVERRUN_LIMIT, handle(), u32(bytes)));
    }

    /**
     * Starts receiving samples.
     *
     * @throws IllegalStateException if the device is already streaming
     */
    public void startRx(SampleListener sampleListener) throws HackRfException {
        startReceive(sampleListener, Mode.RX, "hackrf_start_rx", Native.START_RX);
    }

    /**
     * Configures sweep mode, for {@link #startRxSweep(SampleListener)}. The
     * device steps through each range from its start, receiving
     * {@code bytesPerTuning} bytes at each frequency. Requires USB API version
     * 0x0102.
     *
     * @param rangesMhz start and stop frequency pairs in MHz, 1 to 10 pairs
     * @param bytesPerTuning bytes to receive at each step, a positive multiple
     *     of {@value SampleBuffer#SWEEP_BLOCK_SIZE}
     * @param stepWidthHertz distance between steps
     * @param offsetHertz offset added to each tuned frequency; half the sample
     *     rate is a good choice
     * @param style how to step
     */
    public void initSweep(int[] rangesMhz, int bytesPerTuning, long stepWidthHertz, long offsetHertz,
            SweepStyle style) throws HackRfException {
        Objects.requireNonNull(style, "style");
        if (rangesMhz.length < 2 || rangesMhz.length % 2 != 0 || rangesMhz.length > 2 * Layouts.MAX_SWEEP_RANGES) {
            throw new IllegalArgumentException("Need 1 to " + Layouts.MAX_SWEEP_RANGES
                + " start and stop pairs, not " + rangesMhz.length + " values");
        }
        if (bytesPerTuning <= 0 || bytesPerTuning % Layouts.BYTES_PER_BLOCK != 0) {
            throw new IllegalArgumentException("Bytes per tuning must be a positive multiple of "
                + Layouts.BYTES_PER_BLOCK + ": " + bytesPerTuning);
        }
        if (stepWidthHertz < 1) {
            throw new IllegalArgumentException("Step width out of range: " + stepWidthHertz);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment ranges = arena.allocate(JAVA_SHORT, rangesMhz.length);
            for (int i = 0; i < rangesMhz.length; i++) {
                ranges.setAtIndex(JAVA_SHORT, i, u16(rangesMhz[i]));
            }
            check("hackrf_init_sweep", Native.invoke(Native.INIT_SWEEP, handle(), ranges, rangesMhz.length / 2,
                bytesPerTuning, u32(stepWidthHertz), u32(offsetHertz), style.code()));
        }
    }

    /**
     * Starts a sweep configured by {@link #initSweep}. Each buffer holds several
     * blocks; use {@link SampleBuffer#sweepBlocks()} to read their frequencies.
     * Requires USB API version 0x0104.
     *
     * @throws IllegalStateException if the device is already streaming
     */
    public void startRxSweep(SampleListener sampleListener) throws HackRfException {
        startReceive(sampleListener, Mode.SWEEP, "hackrf_start_rx_sweep", Native.START_RX_SWEEP);
    }

    private void startReceive(SampleListener sampleListener, Mode newMode, String function, MethodHandle start)
            throws HackRfException {
        Objects.requireNonNull(sampleListener, "sampleListener");
        synchronized (lifecycleLock) {
            MemorySegment device = prepareStart();
            listener = sampleListener;
            try {
                check(function, Native.invoke(start, device, sampleBlockCallback, MemorySegment.NULL));
                mode = newMode;
            } catch (HackRfException | RuntimeException e) {
                listener = null;
                throw e;
            }
        }
    }

    /**
     * Starts transmitting. The source is first called to fill every transfer
     * buffer, on this thread, before anything is sent.
     *
     * <p>When the source returns 0 the device finishes sending what it has;
     * {@link #awaitTransmitComplete} waits for that. Call {@link #stopTx()}
     * afterwards in any case.
     *
     * @throws IllegalStateException if the device is already streaming
     */
    public void startTx(TransmitSource transmitSource) throws HackRfException {
        Objects.requireNonNull(transmitSource, "transmitSource");
        synchronized (lifecycleLock) {
            MemorySegment device = prepareStart();
            if (!flushEnabled) {
                // The flush callback reports when the last samples have left the device.
                check("hackrf_enable_tx_flush",
                    Native.invoke(Native.ENABLE_TX_FLUSH, device, flushCallback, MemorySegment.NULL));
                flushEnabled = true;
            }
            transmitFlushed = false;
            transmitComplete = new CountDownLatch(1);
            source = transmitSource;
            try {
                check("hackrf_set_tx_block_complete_callback",
                    Native.invoke(Native.SET_TX_BLOCK_COMPLETE_CALLBACK, device, txBlockCompleteCallback));
                check("hackrf_start_tx", Native.invoke(Native.START_TX, device, sampleBlockCallback,
                    MemorySegment.NULL));
                mode = Mode.TX;
            } catch (HackRfException | RuntimeException e) {
                source = null;
                Native.invoke(Native.SET_TX_BLOCK_COMPLETE_CALLBACK, device, MemorySegment.NULL);
                throw e;
            }
        }
    }

    /**
     * Waits until a transmission whose source has ended has been sent
     * completely. Call {@link #stopTx()} afterwards.
     *
     * @return true if every sample was sent; false if the time ran out or the
     *     transmission was stopped or failed first
     * @throws IllegalStateException if no transmission was started
     */
    public boolean awaitTransmitComplete(long timeout, TimeUnit unit) throws InterruptedException {
        CountDownLatch latch = transmitComplete;
        if (latch == null) {
            throw new IllegalStateException("No transmission was started");
        }
        return latch.await(timeout, unit) && transmitFlushed;
    }

    /** Checks the device can start a stream and clears what the previous one left. */
    private MemorySegment prepareStart() throws HackRfException {
        MemorySegment device = handle();
        if (isStreaming()) {
            throw new IllegalStateException("Device is already streaming");
        }
        // A listener or source may have ended the previous stream without a stop.
        stopStream();
        streamingFailure = null;
        return device;
    }

    /**
     * Stops receiving or sweeping and waits for the listener to return. Does
     * nothing if the device is not streaming. This also stops a transmission.
     *
     * @throws IllegalStateException if called from a {@link SampleListener}
     */
    public void stopRx() throws HackRfException {
        stopStream();
    }

    /**
     * Stops transmitting, discarding samples not yet sent, and waits for the
     * source to return. Does nothing if the device is not streaming. This also
     * stops receiving.
     *
     * @throws IllegalStateException if called from a {@link TransmitSource}
     */
    public void stopTx() throws HackRfException {
        stopStream();
    }

    private void stopStream() throws HackRfException {
        HackRf.rejectCallback();
        synchronized (lifecycleLock) {
            if (mode == Mode.NONE) {
                return;
            }
            boolean transmitting = mode == Mode.TX;
            try {
                if (transmitting) {
                    check("hackrf_stop_tx", Native.invoke(Native.STOP_TX, handle()));
                } else {
                    check("hackrf_stop_rx", Native.invoke(Native.STOP_RX, handle()));
                }
            } finally {
                mode = Mode.NONE;
                listener = null;
                source = null;
                if (transmitting) {
                    Native.invoke(Native.SET_TX_BLOCK_COMPLETE_CALLBACK, handle, MemorySegment.NULL);
                }
            }
        }
    }

    /** Returns true while samples are being received or transmitted. */
    public boolean isStreaming() {
        // Anything but HACKRF_TRUE is a reason for not streaming.
        return Native.invoke(Native.IS_STREAMING, handle()) == 1;
    }

    /** Returns the exception that stopped streaming, or null if there was none. */
    public Throwable streamingFailure() {
        return streamingFailure;
    }

    /**
     * Called through the upcall stub for each receive or transmit transfer. An
     * exception that escaped from here would terminate the JVM, so all of them
     * are caught.
     */
    @SuppressWarnings("unused")
    private int onSampleBlock(MemorySegment transferPointer) {
        boolean previous = HackRf.enterCallback();
        // Closing this arena ends the buffer segment's lifetime, so that a
        // listener that keeps it gets an exception and not freed native memory.
        try (Arena callbackArena = Arena.ofConfined()) {
            if (streamingFailure != null) {
                return 1;
            }
            MemorySegment transfer = transferPointer.reinterpret(Layouts.TRANSFER.byteSize());
            MemorySegment buffer = transfer.get(ADDRESS, Layouts.offset(Layouts.TRANSFER, "buffer"));
            TransmitSource currentSource = source;
            if (currentSource != null) {
                int capacity = transfer.get(JAVA_INT, Layouts.offset(Layouts.TRANSFER, "buffer_length"));
                int written = currentSource.fill(buffer.reinterpret(capacity, callbackArena, null));
                if (written <= 0) {
                    return 1;
                }
                if (written > capacity) {
                    throw new IllegalStateException("TransmitSource wrote " + written + " bytes to a buffer of "
                        + capacity);
                }
                transfer.set(JAVA_INT, Layouts.offset(Layouts.TRANSFER, "valid_length"), written);
                return 0;
            }
            SampleListener currentListener = listener;
            if (currentListener == null) {
                return 1;
            }
            int length = transfer.get(JAVA_INT, Layouts.offset(Layouts.TRANSFER, "valid_length"));
            return currentListener.onSamples(new SampleBuffer(buffer.reinterpret(length, callbackArena, null)))
                ? 0 : 1;
        } catch (Throwable failure) {
            streamingFailure = failure;
            return 1;
        } finally {
            HackRf.exitCallback(previous);
        }
    }

    /** Called through the upcall stub when a transmit transfer has been sent. */
    @SuppressWarnings("unused")
    private void onTxBlockComplete(MemorySegment transferPointer, int success) {
        TransmitSource currentSource = source;
        if (currentSource == null) {
            return;
        }
        boolean previous = HackRf.enterCallback();
        try {
            currentSource.onTransferComplete(success != 0);
        } catch (Throwable failure) {
            // The next fill sees the failure and ends the transmission.
            streamingFailure = failure;
        } finally {
            HackRf.exitCallback(previous);
        }
    }

    /** Called through the upcall stub when the last transmit samples have been sent. */
    @SuppressWarnings("unused")
    private void onFlush(MemorySegment context, int success) {
        transmitFlushed = success != 0;
        CountDownLatch latch = transmitComplete;
        if (latch != null) {
            latch.countDown();
        }
    }

    // ---- Opera Cake ----

    /** Returns the addresses, 0 to 7, of the Opera Cake boards attached. Requires USB API version 0x0105. */
    public int[] operacakeBoards() throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment boards = arena.allocate(Layouts.OPERACAKE_MAX_BOARDS);
            check("hackrf_get_operacake_boards", Native.invoke(Native.GET_OPERACAKE_BOARDS, handle(), boards));
            return IntStream.range(0, Layouts.OPERACAKE_MAX_BOARDS)
                .map(i -> Byte.toUnsignedInt(boards.get(JAVA_BYTE, i)))
                .filter(address -> address != Layouts.OPERACAKE_ADDRESS_INVALID)
                .toArray();
        }
    }

    /** Sets the switching mode of an Opera Cake board. Requires USB API version 0x0105. */
    public void setOperacakeMode(int address, OperacakeMode operacakeMode) throws HackRfException {
        check("hackrf_set_operacake_mode",
            Native.invoke(Native.SET_OPERACAKE_MODE, handle(), operacakeAddress(address), operacakeMode.code()));
    }

    /** Returns the switching mode of an Opera Cake board. Requires USB API version 0x0105. */
    public OperacakeMode operacakeMode(int address) throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(JAVA_INT);
            check("hackrf_get_operacake_mode",
                Native.invoke(Native.GET_OPERACAKE_MODE, handle(), operacakeAddress(address), value));
            return OperacakeMode.fromCode(value.get(JAVA_INT, 0));
        }
    }

    /**
     * Connects A0 and B0 of an Opera Cake board in {@link OperacakeMode#MANUAL}
     * mode. They must go to opposite sides: one port from A1-A4 and one from
     * B1-B4. Requires USB API version 0x0102.
     */
    public void setOperacakePorts(int address, OperacakePort portA, OperacakePort portB) throws HackRfException {
        check("hackrf_set_operacake_ports", Native.invoke(Native.SET_OPERACAKE_PORTS, handle(),
            operacakeAddress(address), (byte) portA.code(), (byte) portB.code()));
    }

    /**
     * Sets the steps of {@link OperacakeMode#TIME} switching, for every board
     * in that mode; at most 16. Requires USB API version 0x0105.
     */
    public void setOperacakeDwellTimes(List<OperacakeDwellTime> dwellTimes) throws HackRfException {
        if (dwellTimes.size() > Layouts.OPERACAKE_MAX_DWELL_TIMES) {
            throw new IllegalArgumentException("At most " + Layouts.OPERACAKE_MAX_DWELL_TIMES + " dwell times");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment array = arena.allocate(Layouts.OPERACAKE_DWELL_TIME, Math.max(1, dwellTimes.size()));
            long size = Layouts.OPERACAKE_DWELL_TIME.byteSize();
            for (int i = 0; i < dwellTimes.size(); i++) {
                OperacakeDwellTime dwell = dwellTimes.get(i);
                array.set(JAVA_INT, i * size + Layouts.offset(Layouts.OPERACAKE_DWELL_TIME, "dwell"),
                    (int) dwell.dwellSamples());
                array.set(JAVA_BYTE, i * size + Layouts.offset(Layouts.OPERACAKE_DWELL_TIME, "port"),
                    (byte) dwell.port().code());
            }
            check("hackrf_set_operacake_dwell_times",
                Native.invoke(Native.SET_OPERACAKE_DWELL_TIMES, handle(), array, (byte) dwellTimes.size()));
        }
    }

    /**
     * Sets the ranges of {@link OperacakeMode#FREQUENCY} switching, for every
     * board in that mode; at most 8. Requires USB API version 0x0103.
     */
    public void setOperacakeFrequencyRanges(List<OperacakeFrequencyRange> ranges) throws HackRfException {
        if (ranges.size() > Layouts.OPERACAKE_MAX_FREQ_RANGES) {
            throw new IllegalArgumentException("At most " + Layouts.OPERACAKE_MAX_FREQ_RANGES + " frequency ranges");
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment array = arena.allocate(Layouts.OPERACAKE_FREQ_RANGE, Math.max(1, ranges.size()));
            long size = Layouts.OPERACAKE_FREQ_RANGE.byteSize();
            for (int i = 0; i < ranges.size(); i++) {
                OperacakeFrequencyRange range = ranges.get(i);
                array.set(JAVA_SHORT, i * size + Layouts.offset(Layouts.OPERACAKE_FREQ_RANGE, "freq_min"),
                    (short) range.minMhz());
                array.set(JAVA_SHORT, i * size + Layouts.offset(Layouts.OPERACAKE_FREQ_RANGE, "freq_max"),
                    (short) range.maxMhz());
                array.set(JAVA_BYTE, i * size + Layouts.offset(Layouts.OPERACAKE_FREQ_RANGE, "port"),
                    (byte) range.port().code());
            }
            check("hackrf_set_operacake_freq_ranges",
                Native.invoke(Native.SET_OPERACAKE_FREQ_RANGES, handle(), array, (byte) ranges.size()));
        }
    }

    /**
     * Tests the GPIO connections of an Opera Cake board and returns the
     * result: 0 means pass. Requires USB API version 0x0103.
     */
    public int operacakeGpioTest(int address) throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(JAVA_SHORT);
            check("hackrf_operacake_gpio_test",
                Native.invoke(Native.OPERACAKE_GPIO_TEST, handle(), operacakeAddress(address), value));
            return Short.toUnsignedInt(value.get(JAVA_SHORT, 0));
        }
    }

    private static byte operacakeAddress(int address) {
        if (address < 0 || address >= Layouts.OPERACAKE_MAX_BOARDS) {
            throw new IllegalArgumentException("Opera Cake address out of range 0 to 7: " + address);
        }
        return (byte) address;
    }

    // ---- Diagnostics ----

    /** Reads the state of the sample loop on the device's M0 core. Requires USB API version 0x0106. */
    public M0State m0State() throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(Layouts.M0_STATE);
            check("hackrf_get_m0_state", Native.invoke(Native.GET_M0_STATE, handle(), s));
            return new M0State(Layouts.u16(s, Layouts.M0_STATE, "requested_mode"),
                Layouts.u16(s, Layouts.M0_STATE, "request_flag") != 0,
                Layouts.u32(s, Layouts.M0_STATE, "active_mode"),
                Layouts.u32(s, Layouts.M0_STATE, "m0_count"),
                Layouts.u32(s, Layouts.M0_STATE, "m4_count"),
                Layouts.u32(s, Layouts.M0_STATE, "num_shortfalls"),
                Layouts.u32(s, Layouts.M0_STATE, "longest_shortfall"),
                Layouts.u32(s, Layouts.M0_STATE, "shortfall_limit"),
                Layouts.u32(s, Layouts.M0_STATE, "threshold"),
                Layouts.u32(s, Layouts.M0_STATE, "next_mode"),
                Layouts.u32(s, Layouts.M0_STATE, "error"));
        }
    }

    /** Reads the result of the device's power-on self-test. Requires USB API version 0x0109. */
    public SelfTest selfTest() throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment s = arena.allocate(Layouts.SELFTEST);
            check("hackrf_read_selftest", Native.invoke(Native.READ_SELFTEST, handle(), s));
            // The message may fill its array without a terminator.
            MemorySegment message = s.asSlice(Layouts.offset(Layouts.SELFTEST, "msg"));
            long end = 0;
            while (end < message.byteSize() && message.get(JAVA_BYTE, end) != 0) {
                end++;
            }
            String text = new String(message.asSlice(0, end).toArray(JAVA_BYTE),
                StandardCharsets.US_ASCII);
            return new SelfTest(s.get(JAVA_BYTE, Layouts.offset(Layouts.SELFTEST, "pass")) != 0, text);
        }
    }

    /**
     * Tests the real-time clock oscillator, which takes about a second, and
     * returns whether it passed. Requires USB API version 0x0109.
     */
    public boolean testRtcOscillator() throws HackRfException {
        return readU8("hackrf_test_rtc_osc", Native.TEST_RTC_OSC) != 0;
    }

    /**
     * Reads an ADC channel of the microcontroller, such as 0 for ADC0_0; add
     * 0x80 to use the alternate pin.
     */
    public int readAdc(int channel) throws HackRfException {
        return readU16("hackrf_read_adc", Native.READ_ADC, u8(channel));
    }

    // ---- Registers ----
    //
    // Direct register access, for debugging. Writes can put the radio in states
    // the library does not expect; retune or reopen the device afterwards.

    /** Reads a register of the MAX2837 transceiver (HackRF One before r9 and others). */
    public int max2837Read(int register) throws HackRfException {
        return readU16("hackrf_max2837_read", Native.MAX2837_READ, u8(register));
    }

    /** Writes a register of the MAX2837 transceiver. */
    public void max2837Write(int register, int value) throws HackRfException {
        check("hackrf_max2837_write", Native.invoke(Native.MAX2837_WRITE, handle(), u8(register), u16(value)));
    }

    /** Reads a register of the MAX2831 transceiver (HackRF One r9 and HackRF Pro). */
    public int max2831Read(int register) throws HackRfException {
        return readU16("hackrf_max2831_read", Native.MAX2831_READ, u8(register));
    }

    /** Writes a register of the MAX2831 transceiver. */
    public void max2831Write(int register, int value) throws HackRfException {
        check("hackrf_max2831_write", Native.invoke(Native.MAX2831_WRITE, handle(), u8(register), u16(value)));
    }

    /** Reads a register of the Si5351C clock generator. */
    public int si5351cRead(int register) throws HackRfException {
        return readU16("hackrf_si5351c_read", Native.SI5351C_READ, u16(register));
    }

    /** Writes a register of the Si5351C clock generator. */
    public void si5351cWrite(int register, int value) throws HackRfException {
        check("hackrf_si5351c_write", Native.invoke(Native.SI5351C_WRITE, handle(), u16(register), u16(value)));
    }

    /** Reads a register of the RFFC5071/5072 mixer. */
    public int rffc5071Read(int register) throws HackRfException {
        return readU16("hackrf_rffc5071_read", Native.RFFC5071_READ, u8(register));
    }

    /** Writes a register of the RFFC5071/5072 mixer. */
    public void rffc5071Write(int register, int value) throws HackRfException {
        check("hackrf_rffc5071_write", Native.invoke(Native.RFFC5071_WRITE, handle(), u8(register), u16(value)));
    }

    /** Reads an FPGA register of a HackRF Pro. */
    public int fpgaReadRegister(int register) throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(JAVA_BYTE);
            check("hackrf_fpga_read_register",
                Native.invoke(Native.FPGA_READ_REGISTER, handle(), u8(register), value));
            return Byte.toUnsignedInt(value.get(JAVA_BYTE, 0));
        }
    }

    /** Writes an FPGA register of a HackRF Pro. */
    public void fpgaWriteRegister(int register, int value) throws HackRfException {
        check("hackrf_fpga_write_register",
            Native.invoke(Native.FPGA_WRITE_REGISTER, handle(), u8(register), u8(value)));
    }

    // ---- Firmware flash and CPLD ----
    //
    // These change the firmware stored on the device. A wrong image leaves it
    // unusable until it is recovered in DFU mode.

    /**
     * Erases the firmware in the SPI flash. Write a new image straight after,
     * or the device will only start in DFU mode.
     */
    public void spiflashErase() throws HackRfException {
        check("hackrf_spiflash_erase", Native.invoke(Native.SPIFLASH_ERASE, handle()));
    }

    /** Writes up to 65535 bytes to the SPI flash at an address. */
    public void spiflashWrite(long address, byte[] data) throws HackRfException {
        if (data.length > 0xFFFF) {
            throw new IllegalArgumentException("At most 65535 bytes per write, not " + data.length);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocateFrom(JAVA_BYTE, data);
            check("hackrf_spiflash_write", Native.invoke(Native.SPIFLASH_WRITE, handle(), u32(address),
                (short) data.length, buffer));
        }
    }

    /** Reads up to 65535 bytes from the SPI flash at an address. */
    public byte[] spiflashRead(long address, int length) throws HackRfException {
        if (length < 0 || length > 0xFFFF) {
            throw new IllegalArgumentException("Length out of range 0 to 65535: " + length);
        }
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment buffer = arena.allocate(Math.max(1, length));
            check("hackrf_spiflash_read", Native.invoke(Native.SPIFLASH_READ, handle(), u32(address),
                (short) length, buffer));
            return buffer.asSlice(0, length).toArray(JAVA_BYTE);
        }
    }

    /** Reads the two status registers of the SPI flash. */
    public byte[] spiflashStatus() throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment status = arena.allocate(2);
            check("hackrf_spiflash_status", Native.invoke(Native.SPIFLASH_STATUS, handle(), status));
            return status.toArray(JAVA_BYTE);
        }
    }

    /** Clears the status registers of the SPI flash. */
    public void spiflashClearStatus() throws HackRfException {
        check("hackrf_spiflash_clear_status", Native.invoke(Native.SPIFLASH_CLEAR_STATUS, handle()));
    }

    /**
     * Writes a bitstream to the CPLD.
     *
     * @deprecated upstream deprecates this: the firmware loads its own CPLD
     *     bitstream at every reset, so the write has no lasting effect
     */
    @Deprecated
    public void cpldWrite(byte[] bitstream) throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            // The library sends 512-byte chunks, so it reads up to the next multiple of 512.
            long padded = Math.max(512, (bitstream.length + 511L) / 512 * 512);
            MemorySegment buffer = arena.allocate(padded);
            MemorySegment.copy(bitstream, 0, buffer, JAVA_BYTE, 0, bitstream.length);
            check("hackrf_cpld_write", Native.invoke(Native.CPLD_WRITE, handle(), buffer, bitstream.length));
        }
    }

    // ---- Lifecycle ----

    /**
     * Stops streaming and releases the device. Closing again does nothing.
     *
     * @throws IllegalStateException if called from a {@link SampleListener} or {@link TransmitSource}
     */
    @Override
    public void close() throws HackRfException {
        HackRf.rejectCallback();
        synchronized (lifecycleLock) {
            if (closed) {
                return;
            }
            try {
                stopStream();
            } finally {
                closed = true;
                synchronized (HackRf.LOCK) {
                    check("hackrf_close", Native.invoke(Native.CLOSE, handle));
                }
            }
        }
    }

    private MemorySegment handle() {
        HackRf.rejectCallback();
        if (closed) {
            throw new IllegalStateException("Device is closed");
        }
        return handle;
    }

    private int readU8(String function, MethodHandle reader) throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(JAVA_BYTE);
            check(function, Native.invoke(reader, handle(), value));
            return Byte.toUnsignedInt(value.get(JAVA_BYTE, 0));
        }
    }

    private int readU16(String function, MethodHandle reader, Object register) throws HackRfException {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment value = arena.allocate(JAVA_SHORT);
            check(function, Native.invoke(reader, handle(), register, value));
            return Short.toUnsignedInt(value.get(JAVA_SHORT, 0));
        }
    }

    private static byte flag(boolean value) {
        return (byte) (value ? 1 : 0);
    }

    private static byte u8(int value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException("Value out of 8-bit range: " + value);
        }
        return (byte) value;
    }

    private static short u16(int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("Value out of 16-bit range: " + value);
        }
        return (short) value;
    }
}
