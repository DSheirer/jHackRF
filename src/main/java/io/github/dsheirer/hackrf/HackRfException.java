package io.github.dsheirer.hackrf;

/** Thrown when a libhackrf function reports an error. */
public class HackRfException extends Exception {
    private static final long serialVersionUID = 1L;

    private final int code;

    /**
     * Creates an exception for a failed native function.
     *
     * @param function name of the native function
     * @param code the {@code hackrf_error} value it returned
     * @param description the library's description of the error, from
     *     {@code hackrf_error_name}, or null
     */
    HackRfException(String function, int code, String description) {
        super(function + " failed: " + HackRfError.fromCode(code).nativeName() + " (" + code + ")"
            + (description == null || description.isEmpty() ? "" : ": " + description));
        this.code = code;
    }

    /** Returns the {@code hackrf_error} value the native function returned. */
    public int code() {
        return code;
    }

    /** Returns the error as an enum, or {@link HackRfError#UNKNOWN} for an unknown code. */
    public HackRfError error() {
        return HackRfError.fromCode(code);
    }
}
