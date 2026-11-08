package io.github.dsheirer.hackrf.internal;

import io.github.dsheirer.libusb.NativeLoader;
import java.lang.foreign.SymbolLookup;
import java.util.List;

/**
 * Loads libhackrf through jlibusb, which supplies the libusb it depends on.
 *
 * <p>By default libhackrf is extracted from the jar, next to jlibusb's copy of
 * libusb. Set the {@value #PATH_PROPERTY} system property to a directory to
 * load libhackrf from there instead; set {@code jlibusb.library.path} to
 * replace libusb.
 */
public final class NativeLibraryLoader {
    /** System property naming a directory that overrides the bundled libhackrf. */
    public static final String PATH_PROPERTY = "jhackrf.library.path";

    private static final String RESOURCE_ROOT = "/jhackrf/native/";

    private NativeLibraryLoader() {
    }

    /** File name of libhackrf on a platform, such as {@code linux-x86_64}. */
    public static String libraryName(String platform) {
        if (platform.startsWith("linux")) {
            return "libhackrf.so";
        }
        if (platform.startsWith("macos")) {
            return "libhackrf.dylib";
        }
        return "hackrf.dll";
    }

    /** Loads libusb and libhackrf and returns a lookup for the symbols of libhackrf. */
    static SymbolLookup load() {
        String name = libraryName(NativeLoader.platform());
        return NativeLoader.load(NativeLibraryLoader.class, RESOURCE_ROOT, List.of(name), PATH_PROPERTY);
    }
}
