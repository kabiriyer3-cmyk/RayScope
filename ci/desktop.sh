#!/usr/bin/env bash
# RayScope for Windows: Compose Desktop app + Xray-core + Temurin JRE.
# Produces dist/RayScope-Setup-NAME.exe (installer) and dist/RayScope-NAME-windows-x64-portable.zip.
# Called from ci/build.sh with the path to gradle as the first argument.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
GRADLE="${1:?gradle path}"
WORK="${RUNNER_TEMP:-/tmp}/rayscope"
# shellcheck disable=SC1090
source "$WORK/version.env"

step() { echo; echo "==================== $* ===================="; }

step "Desktop: toolchain (mingw-w64, nsis, pillow)"
export DEBIAN_FRONTEND=noninteractive
sudo apt-get update -qq >/dev/null || true
sudo apt-get install -y -qq --no-install-recommends gcc-mingw-w64-x86-64 binutils-mingw-w64-x86-64 nsis python3-pil zip unzip >/dev/null
x86_64-w64-mingw32-gcc --version | head -n 1
makensis -VERSION
echo

# numeric version for Windows resources, e.g. 1.0.11
IFS=. read -r V1 V2 V3 _ <<< "${NAME}.0.0.0"
V1="${V1//[^0-9]/}"
V2="${V2//[^0-9]/}"
V3="${V3//[^0-9]/}"
V1="${V1:-0}"
V2="${V2:-0}"
V3="${V3:-0}"
VNUM="$V1.$V2.$V3"
echo "version $NAME -> $VNUM"

step "Desktop: build"
"$GRADLE" -p "$ROOT" :desktop:installDist --no-daemon --stacktrace --console=plain
LIB="desktop/build/install/RayScope/lib"
ls "$LIB" | head -n 60

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

step "Desktop: icon and RayScope.exe"
L="$D/launcher"
mkdir -p "$L"
python3 desktop/launcher/make_icon.py "$L/rayscope.ico" "$L/rayscope.png"
cp desktop/launcher/launcher.c "$L/launcher.c"
sed -e "s/@VERSION_COMMA@/$V1,$V2,$V3/g" -e "s/@VERSION@/$NAME/g" desktop/launcher/launcher.rc > "$L/launcher.rc"
(cd "$L" && x86_64-w64-mingw32-windres -O coff -i launcher.rc -o launcher.res)
x86_64-w64-mingw32-gcc -O2 -s -municode -mwindows -finput-charset=UTF-8 -o "$L/RayScope.exe" "$L/launcher.c" "$L/launcher.res"
ls -la "$L"
file "$L/RayScope.exe" || true

step "Desktop: assemble"
APP="$D/RayScope"
mkdir -p "$APP/core" "$APP/runtime" "$D/jre"
unzip -q "$D/Xray-windows-64.zip" -d "$APP/core"
unzip -q "$D/jre.zip" -d "$D/jre"
JRE_DIR="$(find "$D/jre" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
cp -r "$JRE_DIR"/. "$APP/runtime/"
cp -r "$LIB" "$APP/lib"
cp "$L/RayScope.exe" "$APP/RayScope.exe"
echo "$NAME" > "$APP/version.txt"
test -f "$APP/runtime/bin/javaw.exe"
test -f "$APP/core/xray.exe"
test -f "$APP/RayScope.exe"

cat > "$APP/README.txt" <<'EOF'
RayScope для Windows

Установка: запустите RayScope-Setup-....exe (права администратора не нужны),
ярлыки появятся на рабочем столе и в меню Пуск.
Портативная версия: распакуйте архив целиком в любую папку и запустите RayScope.exe.

Добавьте подписку или вставьте ссылки vless / vmess / trojan / ss / hy2 и нажмите кнопку подключения.

По умолчанию включается системный прокси Windows: браузеры и большинство программ идут через VPN.
В режиме «Только локальный прокси» укажите в программах HTTP 127.0.0.1:10809 или SOCKS5 127.0.0.1:10808.

Ядро Xray лежит в папке core, Java в папке runtime, настройки в %APPDATA%/RayScope.
При закрытии окна программа сворачивается в трей (меняется в настройках).

SmartScreen может предупредить при первом запуске: у файла нет платной цифровой подписи.
Нажмите «Подробнее», затем «Выполнить в любом случае».
EOF

mkdir -p "$ROOT/dist"

step "Desktop: portable zip"
ZIP="$ROOT/dist/RayScope-${NAME}-windows-x64-portable.zip"
rm -f "$ZIP"
(cd "$D" && zip -qr "$ZIP" RayScope)

step "Desktop: installer"
SETUP="$ROOT/dist/RayScope-Setup-${NAME}.exe"
rm -f "$SETUP"
makensis -V2 -INPUTCHARSET UTF8 \
  "-DVERSION=$VNUM" "-DDISPLAY_VERSION=$NAME" "-DSRC=$APP" "-DICON=$L/rayscope.ico" "-DOUTFILE=$SETUP" \
  ci/installer.nsi
test -f "$SETUP"

ls -la "$ROOT/dist"
(cd "$ROOT/dist" && sha256sum *.apk *.zip *.exe > SHA256SUMS.txt)
cat "$ROOT/dist/SHA256SUMS.txt"
