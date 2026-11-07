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

Source: <https://github.com/libusb/libusb>

Licensed under the GNU Lesser General Public License version 2.1 or later. See
`META-INF/licenses/libusb-COPYING`. The exact version in each jar is recorded in
`jhackrf/native/<platform>/build-info.properties`, and
`native/versions.env` in this repository records the release it was built from.

libusb is shipped as a separate shared library so it can be replaced: point the
`jhackrf.library.path` system property at a directory containing your own
build along with libhackrf.

## pthreads4w (Windows only)

Source: <https://github.com/jwinarske/pthreads4w>

Licensed under the Apache License 2.0 and compiled into `hackrf.dll`. See
`META-INF/licenses/pthreads4w-LICENSE` and `META-INF/licenses/pthreads4w-NOTICE`.
