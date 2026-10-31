#!/usr/bin/env bash
# Compile l'APK sans Gradle ni SDK complet.
# Prérequis (Debian/Ubuntu) : apt install openjdk-17-jdk aapt zipalign apksigner dalvik-exchange
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR="${ANDROID_JAR:-$HOME/.cache/bille/android-33.jar}"
if [ ! -f "$ANDROID_JAR" ]; then
  mkdir -p "$(dirname "$ANDROID_JAR")"
  curl -sSfL -o "$ANDROID_JAR" https://raw.githubusercontent.com/Sable/android-platforms/master/android-33/android.jar
fi

rm -rf build && mkdir -p build/gen build/classes
echo "• Vérification des niveaux"
mkdir -p build/check
javac -nowarn -d build/check src/com/mika/bille/Level.java src/com/mika/bille/Levels.java src/com/mika/bille/World.java tools/LevelCheck.java
java -cp build/check LevelCheck | tail -1

echo "• Ressources"
aapt package -f -M AndroidManifest.xml -S res -I "$ANDROID_JAR" -J build/gen -F build/unsigned.apk

echo "• Compilation Java"
javac -nowarn -Xlint:-options -source 8 -target 8 -encoding UTF-8 -bootclasspath "$ANDROID_JAR" \
  -d build/classes $(find src build/gen -name '*.java')

echo "• Dex"
dalvik-exchange --dex --min-sdk-version=24 --output=build/classes.dex build/classes
(cd build && zip -q unsigned.apk classes.dex)

echo "• Alignement et signature"
zipalign -f -p 4 build/unsigned.apk build/aligned.apk
if [ ! -f release.keystore ]; then
  keytool -genkeypair -keystore release.keystore -storepass bille123 -keypass bille123 -alias bille \
    -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Bille Odyssee" >/dev/null 2>&1
fi
apksigner sign --ks release.keystore --ks-pass pass:bille123 --key-pass pass:bille123 --ks-key-alias bille \
  --out BilleOdyssee.apk build/aligned.apk
rm -f BilleOdyssee.apk.idsig
apksigner verify BilleOdyssee.apk && echo "✓ BilleOdyssee.apk ($(du -h BilleOdyssee.apk | cut -f1))"
