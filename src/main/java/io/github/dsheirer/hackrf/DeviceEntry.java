package io.github.dsheirer.hackrf;

/**
 * A connected device, as listed by {@link HackRf#listDevices()}.
 *
 * @param index position in the list, for {@link HackRf#open(int)}
 * @param serialNumber serial number, 32 hexadecimal digits, for
 *     {@link HackRf#open(String)}; null if the library could not read it, which
 *     on some systems includes a device already in use
 * @param usbBoardId board type, from the USB product ID
 * @param busSharingDevices number of other devices on the same USB bus, or -1
 *     if the library could not tell. Other devices on the bus can make the
 *     HackRF drop samples at high sample rates
 */
public record DeviceEntry(int index, String serialNumber, UsbBoardId usbBoardId, int busSharingDevices) {
}
