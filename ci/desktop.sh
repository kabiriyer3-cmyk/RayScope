#!/usr/bin/env bash
# RayScope for Windows: Compose Desktop app + Xray-core + Temurin JRE as a portable zip.
# Called from ci/build.sh with the path to gradle as the first argument.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
GRADLE="${1:?gradle path}"
WORK="${RUNNER_TEMP:-/tmp}/rayscope"
# shellcheck disable=SC1090
source "$WORK/version.env"

step() { echo; echo "==================== $* ===================="; }

step "Desktop: build"
"$GRADLE" -p "$ROOT" :desktop:installDist --no-daemon --stacktrace --console=plain
LIB="desktop/build/install/RayScope/lib"
ls "$LIB" | head -n 50

D="$WORK/desktop"
rm -rf "$D"
mkdir -p "$D"

step "Desktop: Xray core for Windows"
if ! gh release download --repo XTLS/Xray-core --pattern 'Xray-windows-64.zip' --dir "$D"; then
  echo "gh download failed, trying direct link"
  curl -fsSL --retry 3 -o "$D/Xray-windows-64.zip" "https://github.com/XTLS/Xray-core/releases/latest/download/Xray-windows-64.zip"
fi
ls -la "$D"

step "Desktop: Java runtime for Windows"
curl -fsSL --retry 3 -o "$D/jre.zip" "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jre/hotspot/normal/eclipse"
ls -la "$D/jre.zip"

step "Desktop: assemble"
APP="$D/RayScope"
mkdir -p "$APP/core" "$APP/runtime" "$D/jre"
unzip -q "$D/Xray-windows-64.zip" -d "$APP/core"
unzip -q "$D/jre.zip" -d "$D/jre"
JRE_DIR="$(find "$D/jre" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
cp -r "$JRE_DIR"/. "$APP/runtime/"
cp -r "$LIB" "$APP/lib"
test -f "$APP/runtime/bin/javaw.exe"
test -f "$APP/core/xray.exe"

cat > "$APP/RayScope.cmd" <<'EOF'
@echo off
cd /d "%~dp0"
start "" "runtime\bin\javaw.exe" -Xmx768m -Dfile.encoding=UTF-8 -Drayscope.version=@VERSION@ -cp "lib\*" com.rayscope.desktop.MainKt
EOF
sed -i "s/@VERSION@/${NAME}/" "$APP/RayScope.cmd"

cat > "$APP/README.txt" <<'EOF'
RayScope для Windows

1. Распакуйте архив целиком в любую папку (например, в Документы).
2. Запустите RayScope.cmd.
3. Добавьте подписку или вставьте ссылки vless / vmess / trojan / ss / hy2 и нажмите большую кнопку.

По умолчанию включается системный прокси Windows: браузеры и большинство программ идут через VPN.
В режиме «Только локальный прокси» укажите в программах HTTP 127.0.0.1:10809 или SOCKS5 127.0.0.1:10808.

Ядро Xray лежит в папке core, Java в папке runtime, настройки в %APPDATA%/RayScope.
При закрытии окна программа сворачивается в трей (меняется в настройках).
EOF

mkdir -p "$ROOT/dist"
OUT="$ROOT/dist/RayScope-${NAME}-windows-x64.zip"
rm -f "$OUT"
(cd "$D" && zip -qr "$OUT" RayScope)
ls -la "$ROOT/dist"
(cd "$ROOT/dist" && sha256sum *.apk *.zip > SHA256SUMS.txt)
cat "$ROOT/dist/SHA256SUMS.txt"
