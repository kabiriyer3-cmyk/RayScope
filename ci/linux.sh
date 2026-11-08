#!/usr/bin/env bash
# RayScope for Linux: the same Compose Desktop app + Xray-core + sing-box (TUN mode) + Temurin JRE.
# Produces dist/RayScope-NAME-linux-x64.tar.gz (unpack and run) and dist/rayscope_NAME_amd64.deb (Debian, Ubuntu, Mint).
# Called from ci/build.sh after ci/desktop.sh, reuses the app jars built there.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
WORK="${RUNNER_TEMP:-/tmp}/rayscope"
# shellcheck disable=SC1090
source "$WORK/version.env"

step() { echo; echo "==================== $* ===================="; }

LIB="desktop/build/install/RayScope/lib"
test -d "$LIB"
D="$WORK/linux"
rm -rf "$D"
mkdir -p "$D"

step "Linux: Xray core"
if ! gh release download --repo XTLS/Xray-core --pattern 'Xray-linux-64.zip' --dir "$D"; then
  echo "gh download failed, trying direct link"
  curl -fsSL --retry 3 -o "$D/Xray-linux-64.zip" "https://github.com/XTLS/Xray-core/releases/latest/download/Xray-linux-64.zip"
fi

step "Linux: sing-box for TUN mode"
SB_VERSION="${SB_VERSION:-1.13.12}"
SB_TGZ="sing-box-${SB_VERSION}-linux-amd64.tar.gz"
if ! gh release download "v${SB_VERSION}" --repo SagerNet/sing-box --pattern "$SB_TGZ" --dir "$D"; then
  echo "gh download failed, trying direct link"
  curl -fsSL --retry 3 -o "$D/$SB_TGZ" "https://github.com/SagerNet/sing-box/releases/download/v${SB_VERSION}/${SB_TGZ}"
fi
mkdir -p "$D/sb"
tar -xzf "$D/$SB_TGZ" -C "$D/sb"
SB_BIN="$(find "$D/sb" -type f -name 'sing-box' | head -n 1)"
test -n "$SB_BIN"
SB_DIR="$(dirname "$SB_BIN")"

step "Linux: Java runtime"
curl -fsSL --retry 3 -o "$D/jre.tar.gz" "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jre/hotspot/normal/eclipse"
ls -la "$D"

step "Linux: assemble"
APP="$D/RayScope"
mkdir -p "$APP/core" "$APP/runtime" "$D/jre"
unzip -q "$D/Xray-linux-64.zip" -d "$APP/core"
cp "$SB_BIN" "$APP/core/sing-box"
if [ -f "$SB_DIR/LICENSE" ]; then cp "$SB_DIR/LICENSE" "$APP/core/LICENSE-sing-box.txt"; fi
chmod 755 "$APP/core/xray" "$APP/core/sing-box"
tar -xzf "$D/jre.tar.gz" -C "$D/jre"
JRE_DIR="$(find "$D/jre" -mindepth 1 -maxdepth 1 -type d | head -n 1)"
cp -a "$JRE_DIR"/. "$APP/runtime/"
cp -r "$LIB" "$APP/lib"
python3 desktop/launcher/make_icon.py "$D/rayscope.ico" "$APP/rayscope.png"
echo "$NAME" > "$APP/version.txt"

cat > "$APP/rayscope" <<'SH'
#!/bin/sh
# RayScope: the bundled Java runtime with the app; arguments go to the app (--tray from autostart).
HERE="$(dirname "$(readlink -f "$0")")"
exec "$HERE/runtime/bin/java" -Xmx768m -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 \
  -Drayscope.home="$HERE" -cp "$HERE/lib/*" com.rayscope.desktop.MainKt "$@"
SH
chmod 755 "$APP/rayscope"

cat > "$APP/install.sh" <<'SH'
#!/bin/sh
# Adds RayScope from this folder to the application menu of the current user (no root needed).
# Run it again after moving the folder. To remove: rm ~/.local/share/applications/rayscope.desktop
set -e
HERE="$(dirname "$(readlink -f "$0")")"
APPS="${XDG_DATA_HOME:-$HOME/.local/share}/applications"
mkdir -p "$APPS"
cat > "$APPS/rayscope.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=RayScope
GenericName=VPN-клиент
Comment=VPN-клиент на Xray с проверкой серверов
Exec="$HERE/rayscope"
Icon=$HERE/rayscope.png
Terminal=false
Categories=Network;
StartupWMClass=com-rayscope-desktop-MainKt
DESKTOP
command -v update-desktop-database >/dev/null 2>&1 && update-desktop-database "$APPS" >/dev/null 2>&1 || true
echo "RayScope добавлен в меню приложений"
SH
chmod 755 "$APP/install.sh"

cat > "$APP/README.txt" <<'TXT'
RayScope для Linux (x64)

Запуск: ./rayscope в этой папке. В меню приложений: ./install.sh (права root не нужны).
Пакет .deb для Debian, Ubuntu и Mint ставится так: sudo apt install ./rayscope_..._amd64.deb

Добавьте подписку, ссылки vless / vmess / trojan / ss / hy2 или прокси socks5 / http и нажмите кнопку подключения.

Системный прокси настраивается в GNOME и KDE: браузеры и большинство программ идут через VPN.
В режиме «Только локальный прокси» укажите в программах HTTP 127.0.0.1:10809 или SOCKS5 127.0.0.1:10808.
Режим TUN пускает через VPN весь трафик компьютера. При включении система спросит пароль администратора
(нужен pkexec, он есть почти во всех дистрибутивах). TUN работает на sing-box (GPL-3.0), лицензия в core.

Ядро Xray в папке core, Java в папке runtime, настройки в ~/.config/RayScope.
TXT

test -x "$APP/runtime/bin/java"
test -x "$APP/core/xray"
test -f "$APP/core/geoip.dat"
mkdir -p "$ROOT/dist"

step "Linux: tar.gz"
TGZ="$ROOT/dist/RayScope-${NAME}-linux-x64.tar.gz"
rm -f "$TGZ"
(cd "$D" && tar -czf "$TGZ" --owner=0 --group=0 RayScope)

step "Linux: deb"
P="$D/deb"
mkdir -p "$P/DEBIAN" "$P/opt" "$P/usr/bin" "$P/usr/share/applications" "$P/usr/share/icons/hicolor/512x512/apps"
cp -a "$APP" "$P/opt/RayScope"
rm -f "$P/opt/RayScope/install.sh"
ln -s /opt/RayScope/rayscope "$P/usr/bin/rayscope"
cp "$APP/rayscope.png" "$P/usr/share/icons/hicolor/512x512/apps/rayscope.png"
cat > "$P/usr/share/applications/rayscope.desktop" <<'DESKTOP'
[Desktop Entry]
Type=Application
Name=RayScope
GenericName=VPN-клиент
Comment=VPN-клиент на Xray с проверкой серверов
Exec=/opt/RayScope/rayscope
Icon=rayscope
Terminal=false
Categories=Network;
StartupWMClass=com-rayscope-desktop-MainKt
DESKTOP
SIZE="$(du -sk "$P/opt" | cut -f1)"
cat > "$P/DEBIAN/control" <<CONTROL
Package: rayscope
Version: ${NAME}
Architecture: amd64
Maintainer: kabiriyer3-cmyk <kabiriyer3-cmyk@users.noreply.github.com>
Installed-Size: ${SIZE}
Section: net
Priority: optional
Depends: libc6, libx11-6, libxext6, libxrender1, libxtst6, libxi6, libfreetype6, libfontconfig1
Recommends: pkexec | policykit-1
Homepage: https://github.com/kabiriyer3-cmyk/RayScope
Description: VPN client on Xray-core with server diagnostics
 RayScope connects to VLESS, VMess, Trojan, Shadowsocks, Hysteria2 servers
 and plain SOCKS5 / HTTP proxies, checks ping and the real exit IP,
 and sets the system proxy or a TUN adapter. Java runtime and cores included.
CONTROL
find "$P" -type d -exec chmod 755 {} +
chmod 644 "$P/DEBIAN/control"
DEB="$ROOT/dist/rayscope_${NAME}_amd64.deb"
rm -f "$DEB"
dpkg-deb --root-owner-group --build "$P" "$DEB"
dpkg-deb --info "$DEB" | head -n 20

ls -la "$ROOT/dist"
(cd "$ROOT/dist" && sha256sum *.apk *.zip *.exe *.tar.gz *.deb > SHA256SUMS.txt)
cat "$ROOT/dist/SHA256SUMS.txt"
