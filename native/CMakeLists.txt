# Builds libhackrf as a shared library from the upstream sources.
#
# Upstream's CMake files find libusb through pkg-config or fixed paths and
# expect a prebuilt pthreads-win32 DLL on Windows. hackrf.c is simple enough to
# build directly instead.
#
# Used by build-unix.sh and build-windows.ps1; not meant to be run by hand.
cmake_minimum_required(VERSION 3.16)

# The C runtime is linked statically on Windows, so the DLL needs nothing installed.
set(CMAKE_MSVC_RUNTIME_LIBRARY MultiThreaded)

project(jhackrf_native C)

set(HACKRF_SOURCE_DIR "" CACHE PATH "Upstream hackrf source tree")
set(HACKRF_LIBRARY_VERSION "" CACHE STRING "Value of hackrf_library_version()")
set(HACKRF_LIBRARY_RELEASE "" CACHE STRING "Value of hackrf_library_release()")
set(LIBUSB_INCLUDE_DIR "" CACHE PATH "Directory containing libusb.h")
set(LIBUSB_LIBRARY "" CACHE FILEPATH "libusb shared library, or import library on Windows")
set(PTHREADS4W_SOURCE_DIR "" CACHE PATH "pthreads4w source tree (Windows only)")

foreach(var HACKRF_SOURCE_DIR HACKRF_LIBRARY_VERSION HACKRF_LIBRARY_RELEASE LIBUSB_INCLUDE_DIR LIBUSB_LIBRARY)
    if(NOT ${var})
        message(FATAL_ERROR "${var} is required")
    endif()
endforeach()

set(SRC "${HACKRF_SOURCE_DIR}/host/libhackrf/src")
add_library(hackrf SHARED "${SRC}/hackrf.c")
target_include_directories(hackrf PRIVATE "${SRC}" "${LIBUSB_INCLUDE_DIR}")
target_compile_definitions(hackrf PRIVATE
    LIBRARY_VERSION="${HACKRF_LIBRARY_VERSION}"
    LIBRARY_RELEASE="${HACKRF_LIBRARY_RELEASE}")
target_link_libraries(hackrf PRIVATE "${LIBUSB_LIBRARY}")

if(MSVC)
    if(NOT PTHREADS4W_SOURCE_DIR)
        message(FATAL_ERROR "PTHREADS4W_SOURCE_DIR is required on Windows")
    endif()
    # pthread.c includes every pthreads4w module. Compiling it into the DLL
    # makes its DllMain the DLL's, which gives pthreads4w the thread attach and
    # detach notifications it needs to clean up after threads that called it.
    target_sources(hackrf PRIVATE "${PTHREADS4W_SOURCE_DIR}/pthread.c")
    set_source_files_properties("${PTHREADS4W_SOURCE_DIR}/pthread.c" PROPERTIES
        COMPILE_DEFINITIONS HAVE_CONFIG_H)
    target_include_directories(hackrf PRIVATE "${PTHREADS4W_SOURCE_DIR}")
    target_compile_definitions(hackrf PRIVATE _CRT_SECURE_NO_WARNINGS)
    target_link_options(hackrf PRIVATE /INCREMENTAL:NO)
else()
    set(THREADS_PREFER_PTHREAD_FLAG ON)
    find_package(Threads REQUIRED)
    target_compile_options(hackrf PRIVATE -Wall)
    target_link_libraries(hackrf PRIVATE Threads::Threads)
endif()
