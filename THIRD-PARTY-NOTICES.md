# Third-party notices

jHackRF is licensed under the Apache License 2.0. The jar also contains native
libraries from the projects below, which keep their own licenses. Full license
texts are in `META-INF/licenses/` inside the jar.

## libhackrf

Source: <https://github.com/greatscottgadgets/hackrf>

The library sources (`host/libhackrf/src`) are licensed under the BSD 3-Clause
License stated at the top of `hackrf.c`. See
`META-INF/licenses/libhackrf-LICENSE`. Upstream's GPL-2.0 license covers its
firmware, tools and build files, which jHackRF does not use or ship. jHackRF is
an independent project and is not endorsed by Great Scott Gadgets or its
contributors. HackRF is a trademark of Great Scott Gadgets.

## libusb

jHackRF does not contain libusb. It is supplied at runtime by
[jlibusb](https://github.com/DSheirer/jlibusb), which ships it under the GNU
Lesser General Public License version 2.1 or later and documents how to
replace it. jHackRF is built against the libusb headers of the release that
`native/versions.env` records.

## pthreads4w (Windows only)

Source: <https://github.com/jwinarske/pthreads4w>

Licensed under the Apache License 2.0 and compiled into `hackrf.dll`. See
`META-INF/licenses/pthreads4w-LICENSE` and `META-INF/licenses/pthreads4w-NOTICE`.
