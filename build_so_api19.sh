#!/usr/bin/env bash
# Сборка libtgwsproxy.so для Android 4.4 (API 19, armeabi-v7a).
#
# Нужно:
#   * Rust (rustup) с целью armv7-linux-androideabi
#   * Android NDK r25c (последний NDK, умеющий API 19; в r26+ минимум API 21)
#     -> переменная ANDROID_NDK_HOME должна указывать на папку NDK r25c
#
# Запуск:  ANDROID_NDK_HOME=/путь/к/android-ndk-r25c ./build_so_api19.sh
set -euo pipefail

cd "$(dirname "$0")"

if [ -z "${ANDROID_NDK_HOME:-}" ] || [ ! -d "$ANDROID_NDK_HOME" ]; then
  echo "ERROR: задайте ANDROID_NDK_HOME (NDK r25c)." >&2
  exit 1
fi

case "$(uname -s)" in
  Linux*)  HOST_TAG=linux-x86_64 ;;
  Darwin*) HOST_TAG=darwin-x86_64 ;;
  *) echo "ERROR: неподдерживаемая ОС (под Windows используйте WSL или GitHub Actions)" >&2; exit 1 ;;
esac

NDK_BIN="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG/bin"
CLANG="$NDK_BIN/armv7a-linux-androideabi19-clang"

if [ ! -x "$CLANG" ]; then
  echo "ERROR: не найден $CLANG" >&2
  echo "       Нужен именно NDK r25c: в r26 и новее поддержка API 19 удалена." >&2
  exit 1
fi

rustup target add armv7-linux-androideabi

export CARGO_TARGET_ARMV7_LINUX_ANDROIDEABI_LINKER="$CLANG"
export CC_armv7_linux_androideabi="$CLANG"
export AR_armv7_linux_androideabi="$NDK_BIN/llvm-ar"

echo "=== cargo build (armv7-linux-androideabi, API 19) ==="
cargo build --release --target armv7-linux-androideabi

OUT=target/armv7-linux-androideabi/release/libtgwsproxy.so
DEST=app/src/main/jniLibs/armeabi-v7a
mkdir -p "$DEST"
cp "$OUT" "$DEST/libtgwsproxy.so"
echo "Скопировано в $DEST/libtgwsproxy.so ($(stat -c %s "$DEST/libtgwsproxy.so" 2>/dev/null || stat -f %z "$DEST/libtgwsproxy.so") байт)"

echo "=== проверка символов на совместимость с API 19 ==="
bash "$(dirname "$0")/check_so_api19.sh" "$DEST/libtgwsproxy.so"
