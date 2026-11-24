#!/bin/sh
# Builds SameBoy (pinned), its CGB boot ROM and our JNI glue for this machine's OS and CPU into
# pppp-emulator/src/main/resources/native/ (gitignored), where SameBoy.java loads them from the jar.
# Needs: git, make, a C compiler, a JDK (for jni.h) and, for the boot ROM, RGBDS (rgbasm, rgblink, rgbgfx).
# The fast CGB boot ROM: it picks a colour palette for original Game Boy carts like the full one, without the logo
# animation (the cartridge starts at once). BOOT_ROM=path/to/cgb_boot_fast.bin skips assembling it (a local shortcut
# when RGBDS is missing).
set -eu
SAMEBOY_TAG=v1.0.5
HERE=$(cd "$(dirname "$0")" && pwd)
WORK=${SAMEBOY_WORK:-$HERE/build}
OUT=$HERE/../src/main/resources/native
OS=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=$(uname -m)
case "$ARCH" in aarch64|arm64) ARCH=arm64 ;; x86_64|amd64) ARCH=x86_64 ;; esac
case "$OS" in darwin) EXT=dylib; JNI_OS=darwin ;; *) EXT=so; JNI_OS=linux ;; esac
JAVA_HOME=${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)" 2>/dev/null || command -v javac)")")}

mkdir -p "$WORK" "$OUT/$OS-$ARCH"
if [ ! -d "$WORK/sameboy" ]; then
    git -c advice.detachedHead=false clone -q --depth 1 --branch "$SAMEBOY_TAG" https://github.com/LIJI32/SameBoy.git "$WORK/sameboy"
fi
make -C "$WORK/sameboy" lib CONF=release -j"$(getconf _NPROCESSORS_ONLN 2>/dev/null || echo 4)" > "$WORK/make.log" 2>&1 \
    || { tail -30 "$WORK/make.log"; exit 1; }

if [ -n "${BOOT_ROM:-}" ]; then
    cp "$BOOT_ROM" "$OUT/cgb_boot.bin"
else
    make -C "$WORK/sameboy" build/bin/BootROMs/cgb_boot_fast.bin CONF=release > "$WORK/bootroms.log" 2>&1 \
        || { tail -30 "$WORK/bootroms.log"; exit 1; }
    cp "$WORK/sameboy/build/bin/BootROMs/cgb_boot_fast.bin" "$OUT/cgb_boot.bin"
fi

cc -O3 -shared -fPIC -o "$OUT/$OS-$ARCH/libpppp_sameboy.$EXT" "$HERE/sameboy_jni.c" \
    -I"$WORK/sameboy" -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/$JNI_OS" \
    "$WORK/sameboy/build/lib/libsameboy.a" -lm
echo "Built $OUT/$OS-$ARCH/libpppp_sameboy.$EXT and $OUT/cgb_boot.bin (SameBoy $SAMEBOY_TAG)"
