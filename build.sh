#!/usr/bin/env bash
set -e
JAVAC="D:/Program Files/Eclipse Adoptium/jdk-17.0.18.8-hotspot/bin/javac"
JFX_LIB="D:/Users/Downloads/openjfx-21.0.11_windows-x64_bin-sdk/javafx-sdk-21.0.11/lib"
OUT="out"
rm -rf "$OUT" && mkdir -p "$OUT"
FX_FLAGS=(--module-path "$JFX_LIB" --add-modules javafx.controls,javafx.graphics)
echo "JDK : $JAVAC"
echo "JFX : $JFX_LIB"
echo "OUT : $OUT"
echo
echo "==> [1/4] Base: i18n + model"
"$JAVAC" -encoding UTF-8 -d "$OUT" "${FX_FLAGS[@]}" -cp "$OUT" \
  $(find src/com/bmi/i18n -name "*.java") \
  src/com/bmi/model/User.java \
  src/com/bmi/model/BodyRecord.java
echo "==> [2/4] Business: model.ai + model.db"
"$JAVAC" -encoding UTF-8 -d "$OUT" "${FX_FLAGS[@]}" -cp "$OUT" \
  $(find src/com/bmi/model/ai src/com/bmi/model/db -name "*.java" ! -name "JdbcRecordDaoChainTest.java")
echo "==> [3/4] Controller + View"
"$JAVAC" -encoding UTF-8 -d "$OUT" "${FX_FLAGS[@]}" -cp "$OUT" \
  $(find src/com/bmi/controller src/com/bmi/view -name "*.java")
echo "==> [4/4] BmiApplication"
"$JAVAC" -encoding UTF-8 -d "$OUT" "${FX_FLAGS[@]}" -cp "$OUT" \
  src/com/bmi/view/BmiApplication.java
echo "==> Copy resources"
find src \( -name "*.properties" -o -name "*.css" \) | while read f; do
  rel="${f#src/}"
  mkdir -p "out/$(dirname "$rel")"
  cp "$f" "out/$rel"
done
echo "Build successful: 0 errors. Classes at $OUT/"
