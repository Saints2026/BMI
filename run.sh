#!/usr/bin/env bash
set -e
JAVA="D:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot/bin/java"
JFX_LIB="D:/Users/Downloads/openjfx-21.0.11_windows-x64_bin-sdk/javafx-sdk-21.0.11/lib"
OUT="out"
echo "=============================================="
echo " BMI Launch Check"
echo "----------------------------------------------"
echo " JAVA : $JAVA"
echo " JFX  : $JFX_LIB"
echo " OUT  : $OUT"
echo "=============================================="
if [ ! -f "$JAVA" ]; then echo "[ERROR] No JDK at $JAVA" >&2; exit 1; fi
if [ ! -d "$JFX_LIB" ]; then echo "[ERROR] No JFX at $JFX_LIB" >&2; exit 1; fi
if [ ! -d "$OUT" ]; then echo "[ERROR] Run build.sh first" >&2; exit 1; fi
CMD=("$JAVA" \
  --module-path "$JFX_LIB" \
  --add-modules javafx.controls,javafx.graphics,javafx.base \
  --enable-native-access=javafx.graphics \
  -cp "$OUT" \
  com.bmi.view.BmiApplication)
echo "==> Launching..."
"${CMD[@]}"
