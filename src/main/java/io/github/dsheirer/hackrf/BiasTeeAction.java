package io.github.dsheirer.hackrf;

/**
 * What the device does to the bias tee when it enters a mode (off, receive or
 * transmit), for {@link HackRfDevice#setUserBiasTeeOptions}.
 */
public enum BiasTeeAction {
    /** Keep the device's current setting for this mode. */
    UNCHANGED,
    /** Leave the bias tee as it is when entering this mode. */
    NO_CHANGE,
    /** Turn the bias tee on when entering this mode. */
    ENABLE,
    /** Turn the bias tee off when entering this mode. */
    DISABLE
}
