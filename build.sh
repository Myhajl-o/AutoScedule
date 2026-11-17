#!/usr/bin/env bash
#
# Повна збірка AutoSchedule: пакування → вирівнювання → підпис → перевірка.
#
# Результат: app-signed.apk у корені проєкту.
#
# Використання:
#   ./build.sh
#
set -euo pipefail

cd "$(dirname "$0")"

BUILD_TOOLS="$ANDROID_HOME/build-tools/30.0.3"
ZIPALIGN="$BUILD_TOOLS/zipalign"
APKSIGNER="$BUILD_TOOLS/apksigner"
KEYTOOL="keytool"

INPUT_APK="target/autoschedule-1.0.0.apk"
ALIGNED_APK="target/autoschedule-1.0.0-aligned.apk"
OUTPUT_APK="app-signed.apk"

# debug.keystore не зберігається в git — створюємо локальний, якщо його немає.
if [ ! -f debug.keystore ]; then
  echo "== 0/4 Створення debug.keystore"
  "$KEYTOOL" -genkeypair -v \
    -keystore debug.keystore \
    -storepass android -keypass android \
    -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null
fi

echo "== 1/4 Пакування"
mvn -B clean package

# Мохо zipalign зберігає результат в окремий файл; якщо з якоїсь причини
# його немає — вирівнюємо самостійно.
if [ ! -f "$ALIGNED_APK" ]; then
  echo "== 2/4 Вирівнювання (вручну, мохо не створив $ALIGNED_APK)"
  "$ZIPALIGN" -f -p 4 "$INPUT_APK" "$ALIGNED_APK"
else
  echo "== 2/4 Вирівнювання (зроблено під час mvn package)"
fi

"$ZIPALIGN" -c 4 "$ALIGNED_APK" >/dev/null
echo "   вирівнювання OK"

echo "== 3/4 Підпис"
"$APKSIGNER" sign \
  --ks debug.keystore \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$OUTPUT_APK" \
  "$ALIGNED_APK"

echo "== 4/4 Перевірка"
"$APKSIGNER" verify "$OUTPUT_APK"
"$ZIPALIGN" -c 4 "$OUTPUT_APK" >/dev/null

echo
echo "Готово: $OUTPUT_APK ($(du -h "$OUTPUT_APK" | cut -f1))"
sha256sum "$OUTPUT_APK"
