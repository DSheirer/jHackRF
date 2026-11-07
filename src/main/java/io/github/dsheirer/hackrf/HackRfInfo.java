package io.github.dsheirer.hackrf;

import java.util.List;

/** Command line tool that prints the library version and the connected devices. */
public final class HackRfInfo {
    private HackRfInfo() {
    }

    /** Runs the tool. */
    public static void main(String[] args) {
        System.out.println("libhackrf " + HackRf.libraryVersion() + " (release " + HackRf.libraryRelease() + ")");
        List<DeviceEntry> devices;
        try {
            devices = HackRf.listDevices();
        } catch (HackRfException e) {
            // Machines without USB access, such as containers, end up here.
            System.out.println("Unable to list devices: " + e.getMessage());
            return;
        }
        System.out.println("Devices found: " + devices.size());
        for (DeviceEntry entry : devices) {
            System.out.println("Index " + entry.index() + ": " + entry.usbBoardId());
            System.out.println("  Serial number: " + (entry.serialNumber() == null ? "unknown" : entry.serialNumber()));
            if (entry.busSharingDevices() > 0) {
                System.out.println("  Shares its USB bus with " + entry.busSharingDevices() + " other devices");
            }
            try (HackRfDevice device = HackRf.open(entry.index())) {
                int api = device.usbApiVersion();
                System.out.println("  Board ID:      " + device.boardId());
                System.out.println("  Firmware:      " + device.firmwareVersion() + " (API "
                    + HackRfDevice.formatUsbApiVersion(api) + ")");
                System.out.println("  " + device.boardInfo());
                if (api >= 0x0106) {
                    System.out.println("  Revision:      " + device.boardRevision());
                }
            } catch (HackRfException e) {
                System.out.println("  Unable to open: " + e.getMessage());
            }
        }
    }
}
