# jHackRF

Java bindings for [libhackrf](https://github.com/greatscottgadgets/hackrf), the
driver for the HackRF One, HackRF Pro, rad1o and Jawbreaker software-defined
radios, built on the Java Foreign Function & Memory API. The jar contains the
native libraries, so there is nothing else to install.

| OS | x86-64 | aarch64 |
|---------|----|----|
| Linux (glibc 2.34+, such as Ubuntu 22.04, Debian 12, RHEL 9) | ✓ | ✓ |
| macOS 11+ | ✓ | ✓ |
| Windows | ✓ | ✓ |

Requires Java 23 or later.

## Getting it

Download `jhackrf-<version>.jar` from
[Releases](https://github.com/DSheirer/jHackRF/releases). The rolling
[`nightly`](https://github.com/DSheirer/jHackRF/releases/tag/nightly)
pre-release tracks `main` of this project and `main` of upstream.

Check that your machine can see a device:

```
java -jar jhackrf-<version>.jar
```

## Using it

```java
try (HackRfDevice device = HackRf.open()) {
    device.setSampleRate(10_000_000);
    device.setFrequency(100_000_000L);
    device.setLnaGain(16);                  // 0-40 dB, 8 dB steps
    device.setVgaGain(20);                  // 0-62 dB, 2 dB steps

    device.startRx(buffer -> {
        byte[] iq = buffer.copyBytes();     // interleaved signed 8-bit I, Q
        queue.offer(iq);
        return true;                        // false stops the stream
    });
    Thread.sleep(10_000);
}
```

`HackRf.listDevices()` describes the connected devices, and
`HackRf.open(String serial)` or `HackRf.open(int index)` opens one of them. Set
the sample rate, frequency and gains before streaming: the device keeps its
previous values. Setting the sample rate also resets the baseband filter, so
call `setBasebandFilterBandwidth` after it.

Listeners run on libusb's event thread, which serves every open device, so hand
slow work to another thread. A listener must not call any device method; those
throw `IllegalStateException` there. `buffer.samples()` gives zero-copy access,
valid only until the listener returns; later use throws an exception rather
than reading freed memory. An exception thrown by a listener stops the stream
and is available from `streamingFailure()`.

### Transmitting

Transmitting needs a license for the frequency you use, or a dummy load.

```java
device.setTxVgaGain(20);
device.startTx(buffer -> {
    int n = fillWithSamples(buffer);        // bytes written; 0 ends the transmission
    return n;
});
device.awaitTransmitComplete(10, TimeUnit.SECONDS);
device.stopTx();
```

### Sweeping

`initSweep` and `startRxSweep` step through frequency ranges, as
`hackrf_sweep` does. `buffer.sweepBlocks()` splits each buffer into blocks with
the frequency each was received at.

### Other features

Opera Cake antenna switching, bias tee control, clock input and output,
hardware triggering, HackRF Pro connectors and FPGA bitstreams, self-tests, and
direct access to the radio's registers and firmware flash are also available.
Every function in `hackrf.h` has a binding. All are exposed except
`hackrf_set_operacake_ranges`, which upstream deprecates in favour of
`hackrf_set_operacake_freq_ranges` (`setOperacakeFrequencyRanges`).

### Native access

Java prints a warning, which future versions will turn into an error, unless
you allow native access:

```
java --enable-native-access=ALL-UNNAMED -cp app.jar:jhackrf.jar my.App
```

On the module path, use `--enable-native-access=io.github.dsheirer.hackrf`. In
your own executable jar, add `Enable-Native-Access: ALL-UNNAMED` to the manifest.

### USB access

- **Linux:** install upstream's
  [udev rules](https://github.com/greatscottgadgets/hackrf/blob/main/host/libhackrf/53-hackrf.rules)
  to use a device without root.
- **Windows:** the device needs the WinUSB driver, which you can install with
  [Zadig](https://zadig.akeo.ie/) if Windows has not assigned it.

### Native library location

Libraries are extracted to the temporary directory at startup. Set
`-Djhackrf.library.path=<dir>` to load them from a directory instead: for
example, to supply your own build of libusb, or if the temporary directory is
mounted `noexec`.

## Building

You need JDK 23+, CMake 3.16+, a C compiler, make and git. On Windows use Visual
Studio and 7-Zip.

```
./native/build-unix.sh                                # Linux and macOS
pwsh native/build-windows.ps1 -Arch x64               # Windows; or -Arch ARM64
./gradlew build                                       # jar in build/libs
./gradlew test -PhardwareTest=true                    # also test a connected device
```

A local jar contains only your platform. The hardware tests are receive-only:
they transmit nothing and write nothing to the device's flash memory.

The native build compiles upstream's `hackrf.c` with
[`native/CMakeLists.txt`](native/CMakeLists.txt) rather than upstream's CMake
files. On Windows it compiles
[pthreads4w](https://github.com/jwinarske/pthreads4w) into `hackrf.dll`, since
libhackrf uses POSIX threads.

## Releases

jHackRF versions follow upstream releases: jHackRF 2026.01.3 wraps libhackrf
from HackRF release 2026.01.3 (`hackrf_library_version()` reports the libhackrf
version, 0.9.2). [`native/versions.env`](native/versions.env) pins the upstream
tag (`HACKRF_REF`) and the release it names (`HACKRF_VERSION`); the build fails
if they disagree.

| Trigger | What happens |
|---------|--------------|
| Pull request | Builds and tests all six platforms; publishes nothing |
| Merge to `main` | Replaces the `nightly` pre-release, built against upstream `main` |
| Daily schedule | The same, if this project or upstream has changed |
| Tag `vX.Y.Z` | Publishes a release built from the pinned upstream tag |

To release, tag the pinned version: `git tag v2026.01.3 && git push origin v2026.01.3`.
The workflow rejects a tag that does not match the pin. For a Java-only fix on
the same upstream version add a fourth number, such as `v2026.01.3.1`; for a
pre-release add a suffix, such as `v2026.01.3-rc1`.

To adopt a new upstream release, change `HACKRF_REF` and `HACKRF_VERSION` in
`native/versions.env`, which also moves the jHackRF version. If upstream added
functions, the build lists them; add them to `Native.java`. Nightly builds only
warn about them.

## License

Apache License 2.0. The bundled native libraries have their own licenses: see
[THIRD-PARTY-NOTICES.md](THIRD-PARTY-NOTICES.md).
