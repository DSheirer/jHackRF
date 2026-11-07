package io.github.dsheirer.hackrf;

import java.util.Locale;

/**
 * Board type identified from a device's USB product ID, an
 * {@code enum hackrf_usb_board_id} value. Known without opening the device.
 *
 * @param code the USB product ID, such as {@code 0x6089} for a HackRF One
 * @param name the library's name for it
 */
public record UsbBoardId(int code, String name) {
    /** {@code USB_BOARD_ID_JAWBREAKER}. */
    public static final int JAWBREAKER = 0x604B;
    /** {@code USB_BOARD_ID_HACKRF_ONE}, also used by HackRF Pro. */
    public static final int HACKRF_ONE = 0x6089;
    /** {@code USB_BOARD_ID_RAD1O}. */
    public static final int RAD1O = 0xCC15;

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "%s (0x%04X)", name, code);
    }
}
