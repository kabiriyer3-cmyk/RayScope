# RayScope: installs the TUN helper. Runs once as root through pkexec; after that RayScope switches TUN on and off
# without the password until the next reboot (/run is emptied on boot, the helper process is gone with it).
# $1 text of tun-helper.sh, $2 sing-box from the RayScope folder, $3 helper version.
U="${PKEXEC_UID:-}"
case "$U" in ''|*[!0-9]*) echo "pkexec не передал UID пользователя"; exit 2 ;; esac
G="$(id -g "$U" 2>/dev/null)"
case "$G" in ''|*[!0-9]*) G="$U" ;; esac
D="/run/rayscope-$U"
UNIT="rayscope-tun-$U"

running() { [ -r "/proc/$1/cmdline" ] && tr '\0' ' ' < "/proc/$1/cmdline" | grep -qF "$2"; }

# the helper of an older RayScope: ask it to quit, it takes its sing-box down with it
if [ -p "$D/ctl" ]; then timeout 3 sh -c 'printf "quit\n" > "$1"' sh "$D/ctl" >/dev/null 2>&1; fi
if command -v systemctl >/dev/null 2>&1; then systemctl stop "$UNIT.service" >/dev/null 2>&1; fi
old="$(cat "$D/pid" 2>/dev/null)"
case "$old" in ''|*[!0-9]*) ;; *)
  if running "$old" "$D/helper.sh"; then
    kill "$old" 2>/dev/null
    i=0
    while running "$old" "$D/helper.sh" && [ $i -lt 30 ]; do sleep 0.1; i=$((i+1)); done
    running "$old" "$D/helper.sh" && kill -9 "$old" 2>/dev/null
  fi ;;
esac
# its sing-box, if the helper was killed hard
set -- "$1" "$2" "$3" $(cat "$D/state" 2>/dev/null)
if [ "$4" = run ]; then
  case "$6" in ''|*[!0-9]*) ;; *) running "$6" "$D/sing-box" && kill "$6" 2>/dev/null && sleep 1 ;; esac
fi

rm -rf "$D"
mkdir -m 755 "$D" || { echo "не удалось создать $D"; exit 3; }
cp "$2" "$D/sing-box" && chmod 755 "$D/sing-box" || { echo "не удалось скопировать sing-box: $2"; exit 4; }
printf '%s\n' "$1" > "$D/helper.sh" && chmod 644 "$D/helper.sh" || exit 4
printf '%s\n' "$3" > "$D/version"
mkfifo -m 600 "$D/ctl" && chown "$U:$G" "$D/ctl" || { echo "не удалось создать канал управления"; exit 5; }
: > "$D/tun.log" && chown "$U:$G" "$D/tun.log" && chmod 600 "$D/tun.log"

# a system service survives logout; otherwise just detach from RayScope
if command -v systemd-run >/dev/null 2>&1 && [ -d /run/systemd/system ] &&
  systemd-run --quiet --collect --unit="$UNIT" --description="RayScope TUN helper" -p TimeoutStopSec=5 \
    /bin/sh "$D/helper.sh" "$U" "$G" >/dev/null 2>&1; then
  :
elif command -v setsid >/dev/null 2>&1; then
  setsid /bin/sh "$D/helper.sh" "$U" "$G" </dev/null >/dev/null 2>&1 &
else
  nohup /bin/sh "$D/helper.sh" "$U" "$G" </dev/null >/dev/null 2>&1 &
fi
i=0
while [ $i -lt 50 ]; do
  [ -f "$D/pid" ] && exit 0
  sleep 0.1
  i=$((i+1))
done
echo "помощник TUN не запустился"
exit 6
