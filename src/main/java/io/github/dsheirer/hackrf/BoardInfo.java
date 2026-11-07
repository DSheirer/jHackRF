package io.github.dsheirer.hackrf;

import java.util.Locale;

/**
 * Identity of a device, read from its microcontroller.
 *
 * @param partId1 first word of the microcontroller part ID
 * @param partId2 second word of the microcontroller part ID
 * @param serialNumber serial number as 32 hexadecimal digits, the form
 *     {@link HackRf#open(String)} takes
 */
public record BoardInfo(long partId1, long partId2, String serialNumber) {
    @Override
    public String toString() {
        return String.format(Locale.ROOT, "part ID 0x%08x 0x%08x, serial %s", partId1, partId2, serialNumber);
    }
}
