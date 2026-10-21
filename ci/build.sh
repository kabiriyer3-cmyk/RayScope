#!/usr/bin/env bash
# RayScope CI build: Java, Android SDK, Gradle, Xray core, signing, APK, Windows build.
# Does not depend on setup-android / setup-gradle actions.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

XRAY_LIB_TAG="${XRAY_LIB_TAG:-v26.10.17}"
GRADLE_VERSION="${GRADLE_VERSION:-8.11.1}"
CMDLINE_TOOLS_BUILD="${CMDLINE_TOOLS_BUILD:-14742923}"
COMPILE_SDK="${COMPILE_SDK:-35}"
BUILD_TOOLS="${BUILD_TOOLS:-35.0.0}"
WORK="${RUNNER_TEMP:-/tmp}/rayscope"
mkdir -p "$WORK"

step() { echo; echo "==================== $* ===================="; }

# ---------------------------------------------------------------- version
step "Version"
REF="${GITHUB_REF:-}"
if [[ "$REF" == refs/tags/v* ]]; then
  VERSION_NAME="${REF#refs/tags/v}"
  TAG="${REF#refs/tags/}"
else
  VERSION_NAME="1.0.${RUN_NUMBER:-0}"
  TAG="v$VERSION_NAME"
fi
export VERSION_NAME
export VERSION_CODE="${RUN_NUMBER:-1}"
printf 'TAG=%s\nNAME=%s\n' "$TAG" "$VERSION_NAME" > "$WORK/version.env"
echo "version $VERSION_NAME (code $VERSION_CODE), tag $TAG"

# ---------------------------------------------------------------- java
step "Java"
if [ -n "${JAVA_HOME_17_X64:-}" ] && [ -d "${JAVA_HOME_17_X64}" ]; then
  export JAVA_HOME="$JAVA_HOME_17_X64"
  export PATH="$JAVA_HOME/bin:$PATH"
fi
java -version 2>&1

# ---------------------------------------------------------------- android sdk
step "Android SDK"

install_own_sdk() {
  echo "Installing own Android cmdline-tools"
  SDK="$WORK/android-sdk"
  mkdir -p "$SDK/cmdline-tools"
  local ok=0
  for b in "$CMDLINE_TOOLS_BUILD" 13114758 11076708; do
    if curl -fsSL --retry 3 -o "$WORK/clt.zip" "https://dl.google.com/android/repository/commandlinetools-linux-${b}_latest.zip"; then
      echo "cmdline-tools build $b"
      ok=1
      break
    fi
  done
  if [ "$ok" != 1 ]; then
    echo "ERROR: could not download Android cmdline-tools"
    exit 1
  fi
  rm -rf "$WORK/clt"
  unzip -q "$WORK/clt.zip" -d "$WORK/clt"
  rm -rf "$SDK/cmdline-tools/latest"
  mv "$WORK/clt/cmdline-tools" "$SDK/cmdline-tools/latest"
  SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
}

install_packages() {
  (yes 2>/dev/null || true) | "$SDKMANAGER" --sdk_root="$SDK" --licenses > /dev/null 2>&1 || true
  "$SDKMANAGER" --sdk_root="$SDK" --install \
    "platforms;android-${COMPILE_SDK}" "build-tools;${BUILD_TOOLS}" "platform-tools" > "$WORK/sdkmanager.log" 2>&1
}

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
SDKMANAGER=""
if [ -n "$SDK" ] && [ -d "$SDK" ]; then
  for p in "$SDK/cmdline-tools/latest/bin/sdkmanager" "$SDK"/cmdline-tools/*/bin/sdkmanager; do
    if [ -x "$p" ]; then
      SDKMANAGER="$p"
      break
    fi
  done
fi
if [ -z "$SDKMANAGER" ]; then
  echo "Preinstalled SDK not found"
  install_own_sdk
fi
echo "SDK: $SDK"
echo "sdkmanager: $SDKMANAGER"

if ! install_packages; then
  echo "sdkmanager failed with preinstalled SDK:"
  tail -n 30 "$WORK/sdkmanager.log" || true
  install_own_sdk
  if ! install_packages; then
    echo "ERROR: sdkmanager failed:"
    tail -n 60 "$WORK/sdkmanager.log" || true
    exit 1
  fi
fi
export ANDROID_HOME="$SDK"
export ANDROID_SDK_ROOT="$SDK"
printf 'sdk.dir=%s\n' "$SDK" > local.properties
ls "$SDK/platforms" "$SDK/build-tools" || true

# ---------------------------------------------------------------- gradle
step "Gradle $GRADLE_VERSION"
GRADLE_DIR="$WORK/gradle-$GRADLE_VERSION"
if [ ! -x "$GRADLE_DIR/bin/gradle" ]; then
  curl -fsSL --retry 3 -o "$WORK/gradle.zip" "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
  unzip -q -o "$WORK/gradle.zip" -d "$WORK"
fi
GRADLE="$GRADLE_DIR/bin/gradle"
"$GRADLE" --version

# ---------------------------------------------------------------- xray core
step "Xray core $XRAY_LIB_TAG"
mkdir -p app/libs
rm -f app/libs/*.aar
if ! curl -fsSL --retry 3 -o app/libs/libv2ray.aar "https://github.com/2dust/AndroidLibXrayLite/releases/download/${XRAY_LIB_TAG}/libv2ray.aar"; then
  echo "Direct download failed, trying gh"
  rm -f app/libs/libv2ray.aar
  gh release download "$XRAY_LIB_TAG" --repo 2dust/AndroidLibXrayLite --pattern "*.aar" --dir app/libs
fi
ls -la app/libs

# ---------------------------------------------------------------- signing
step "Signing"
KS="$WORK/release.p12"
if [ -n "${KS_B64:-}" ]; then
  echo "$KS_B64" | base64 -d > "$KS"
  export KEYSTORE_PASSWORD="${KS_PASS:-}"
  export KEY_ALIAS="${K_ALIAS:-}"
  export KEY_PASSWORD="${K_PASS:-}"
  echo "Using keystore from repository secrets"
else
  base64 -d keystore/release.p12.b64 > "$KS"
  export KEYSTORE_PASSWORD=rayscope123
  export KEY_ALIAS=rayscope
  export KEY_PASSWORD=rayscope123
  echo "::warning::Using the public demo keystore. Add KEYSTORE_BASE64 / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD secrets for your own key."
fi
export KEYSTORE_FILE="$KS"

# ---------------------------------------------------------------- build
step "Build"
"$GRADLE" -p "$ROOT" :app:assembleRelease --no-daemon --stacktrace --console=plain

# ---------------------------------------------------------------- collect
step "Collect APKs"
rm -rf dist
mkdir -p dist
shopt -s nullglob
apks=(app/build/outputs/apk/release/*.apk)
if [ ${#apks[@]} -eq 0 ]; then
  echo "ERROR: no APKs produced"
  exit 1
fi
for f in "${apks[@]}"; do
  b="$(basename "$f" .apk)"
  abi="${b#app-}"
  abi="${abi%-release}"
  cp "$f" "dist/RayScope-${VERSION_NAME}-${abi}.apk"
done
(cd dist && sha256sum *.apk > SHA256SUMS.txt)
ls -la dist

# ---------------------------------------------------------------- windows
bash ci/desktop.sh "$GRADLE"
