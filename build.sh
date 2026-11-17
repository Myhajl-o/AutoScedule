#!/usr/bin/env bash
#
# Full AutoSchedule build: packaging → alignment → signing → verification.
#
# Result: app-signed.apk in the project root.
#
# Usage:
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

# debug.keystore is not stored in git — create a local one if it is missing.
if [ ! -f debug.keystore ]; then
  echo "== 0/4 Creating debug.keystore"
  "$KEYTOOL" -genkeypair -v \
    -keystore debug.keystore \
    -storepass android -keypass android \
    -alias androiddebugkey \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null
fi

echo "== 1/4 Packaging"
mvn -B clean package

# Moho's zipalign saves the result to a separate file; if for some reason
# it is missing — align manually.
if [ ! -f "$ALIGNED_APK" ]; then
  echo "== 2/4 Alignment (manual, Moho did not create $ALIGNED_APK)"
  "$ZIPALIGN" -f -p 4 "$INPUT_APK" "$ALIGNED_APK"
else
  echo "== 2/4 Alignment (done during mvn package)"
fi

"$ZIPALIGN" -c 4 "$ALIGNED_APK" >/dev/null
echo "   alignment OK"

echo "== 3/4 Signing"
"$APKSIGNER" sign \
  --ks debug.keystore \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$OUTPUT_APK" \
  "$ALIGNED_APK"

echo "== 4/4 Verification"
"$APKSIGNER" verify "$OUTPUT_APK"
"$ZIPALIGN" -c 4 "$OUTPUT_APK" >/dev/null

echo
echo "Done: $OUTPUT_APK ($(du -h "$OUTPUT_APK" | cut -f1))"
sha256sum "$OUTPUT_APK"
