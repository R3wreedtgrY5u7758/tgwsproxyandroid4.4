#!/usr/bin/env bash
# Проверяет, что ВСЕ обычные (не weak) символы, которые импортирует .so,
# есть в системных библиотеках Android 4.4 (по заглушкам NDK для API 19).
# Если что-то не найдено — на 4.4 загрузка упадёт с "cannot locate symbol".
#
#   ANDROID_NDK_HOME=... ./check_so_api19.sh app/src/main/jniLibs/armeabi-v7a/libtgwsproxy.so
set -euo pipefail

SO="${1:?укажите путь к .so}"
: "${ANDROID_NDK_HOME:?задайте ANDROID_NDK_HOME (NDK r25c)}"

case "$(uname -s)" in
  Linux*)  HOST_TAG=linux-x86_64 ;;
  Darwin*) HOST_TAG=darwin-x86_64 ;;
  *) echo "неподдерживаемая ОС" >&2; exit 1 ;;
esac

PREBUILT="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$HOST_TAG"
READELF="$PREBUILT/bin/llvm-readelf"
[ -x "$READELF" ] || READELF="$(command -v readelf)"
STUBDIR="${STUBDIR:-$PREBUILT/sysroot/usr/lib/arm-linux-androideabi/19}"

tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT

# Что .so импортирует (кроме weak — они спокойно остаются неразрешёнными).
"$READELF" --dyn-syms -W "$SO" \
  | awk '$7=="UND" && $5!="WEAK" && $8!="" {print $8}' \
  | sed 's/@.*//' | sort -u > "$tmp/need.txt"

# Что экспортирует сама .so (наши шимы epoll_create1 и т.п.).
"$READELF" --dyn-syms -W "$SO" \
  | awk '$7!="UND" && $8!="" {print $8}' | sed 's/@.*//' | sort -u > "$tmp/own.txt"

# Что есть в системе API 19.
: > "$tmp/have.txt"
for lib in libc.so libm.so libdl.so liblog.so; do
  f="$STUBDIR/$lib"
  [ -f "$f" ] || { echo "WARN: нет $f" >&2; continue; }
  "$READELF" --dyn-syms -W "$f" \
    | awk '$7!="UND" && $8!="" {print $8}' | sed 's/@.*//' >> "$tmp/have.txt"
done
sort -u "$tmp/have.txt" -o "$tmp/have.txt"
cat "$tmp/own.txt" >> "$tmp/have.txt"; sort -u "$tmp/have.txt" -o "$tmp/have.txt"

missing="$(comm -23 "$tmp/need.txt" "$tmp/have.txt")"

echo "Импортируется (не weak): $(wc -l < "$tmp/need.txt") символов"
if [ -z "$missing" ]; then
  echo "OK: все импорты присутствуют в Android API 19."
else
  echo "ОШИБКА: этих символов нет в Android 4.4 (API 19):" >&2
  echo "$missing" | sed 's/^/   - /' >&2
  echo >&2
  echo "Добавьте для них шимы в src/compat_api19.rs (по образцу epoll_create1)" >&2
  echo "и пересоберите. Либо пришлите этот список разработчику." >&2
  exit 2
fi
