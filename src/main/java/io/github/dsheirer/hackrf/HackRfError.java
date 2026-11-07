package io.github.dsheirer.hackrf;

/** Error codes of {@code enum hackrf_error}. */
public enum HackRfError {
    /** {@code HACKRF_ERROR_INVALID_PARAM}: an argument was out of range. */
    INVALID_PARAM(-2, "HACKRF_ERROR_INVALID_PARAM"),
    /** {@code HACKRF_ERROR_NOT_FOUND}: no matching device is connected. */
    NOT_FOUND(-5, "HACKRF_ERROR_NOT_FOUND"),
    /** {@code HACKRF_ERROR_BUSY}: the device is already streaming. */
    BUSY(-6, "HACKRF_ERROR_BUSY"),
    /** {@code HACKRF_ERROR_NO_MEM}: memory allocation failed. */
    NO_MEM(-11, "HACKRF_ERROR_NO_MEM"),
    /** {@code HACKRF_ERROR_LIBUSB}: a USB operation failed. */
    LIBUSB(-1000, "HACKRF_ERROR_LIBUSB"),
    /** {@code HACKRF_ERROR_THREAD}: the transfer thread could not be started or stopped. */
    THREAD(-1001, "HACKRF_ERROR_THREAD"),
    /** {@code HACKRF_ERROR_STREAMING_THREAD_ERR}: the transfer thread is not running. */
    STREAMING_THREAD_ERR(-1002, "HACKRF_ERROR_STREAMING_THREAD_ERR"),
    /** {@code HACKRF_ERROR_STREAMING_STOPPED}: streaming has stopped. */
    STREAMING_STOPPED(-1003, "HACKRF_ERROR_STREAMING_STOPPED"),
    /** {@code HACKRF_ERROR_STREAMING_EXIT_CALLED}: streaming is shutting down. */
    STREAMING_EXIT_CALLED(-1004, "HACKRF_ERROR_STREAMING_EXIT_CALLED"),
    /** {@code HACKRF_ERROR_USB_API_VERSION}: the device firmware is too old for the function. */
    USB_API_VERSION(-1005, "HACKRF_ERROR_USB_API_VERSION"),
    /** {@code HACKRF_ERROR_NOT_LAST_DEVICE}: devices are still open. */
    NOT_LAST_DEVICE(-2000, "HACKRF_ERROR_NOT_LAST_DEVICE"),
    /** {@code HACKRF_ERROR_OTHER}: an unspecified error. */
    OTHER(-9999, "HACKRF_ERROR_OTHER"),
    /** A code this version of jHackRF does not know. */
    UNKNOWN(Integer.MIN_VALUE, "unknown error");

    private final int code;
    private final String nativeName;

    HackRfError(int code, String nativeName) {
        this.code = code;
        this.nativeName = nativeName;
    }

    /** Returns the native error code. */
    public int code() {
        return code;
    }

    /** Returns the C name of the error, such as {@code HACKRF_ERROR_NOT_FOUND}. */
    public String nativeName() {
        return nativeName;
    }

    /** Returns the error for a native code, or {@link #UNKNOWN} if it is unknown. */
    public static HackRfError fromCode(int code) {
        for (HackRfError error : values()) {
            if (error.code == code) {
                return error;
            }
        }
        return UNKNOWN;
    }
}
