# RayScope TUN helper: started once through pkexec, lives as root until the reboot and runs sing-box on request,
# so the administrator password is asked only once. Commands, one per line, come through the ctl FIFO that only
# this user can write:  start TOKEN OWNER_PID CONFIG_JSON  |  stop TOKEN  |  quit
# sing-box runs as the user with just the network capabilities (setpriv), as root only when that is impossible.
# It is stopped when OWNER_PID (RayScope) exits, so a crash never leaves the PC without internet.
U="$1"
G="$2"
D="$(dirname "$0")"
LOG="$D/tun.log"
CAPS="+net_admin,+net_raw,+net_bind_service"
cur=""
runner=""

state() { printf '%s\n' "$*" > "$D/state.tmp" && mv -f "$D/state.tmp" "$D/state"; }
st() { cat "$D/state" 2>/dev/null; }

can_drop() {
  command -v setpriv >/dev/null 2>&1 || return 1
  amb="$(setpriv --reuid="$U" --regid="$G" --init-groups --inh-caps="$CAPS" --ambient-caps="$CAPS" -- \
    /bin/sh -c 'grep "^CapAmb" /proc/self/status' 2>/dev/null)"
  amb="${amb#*:}"
  case "$amb" in *[1-9a-fA-F]*) return 0 ;; esac
  return 1
}

if can_drop; then MODE=user; else MODE=root; fi

# one session: sing-box for TOKEN until it exits, is stopped or OWNER_PID is gone
run() {
  tok="$1"
  owner="$2"
  mode="$MODE"
  cfg="$D/tun.json"
  code=1
  while :; do
    t0="$(date +%s)"
    if [ "$mode" = user ]; then
      setpriv --reuid="$U" --regid="$G" --init-groups --inh-caps="$CAPS" --ambient-caps="$CAPS" -- \
        "$D/sing-box" run -c "$cfg" -D "$D" --disable-color </dev/null >>"$LOG" 2>&1 &
    else
      # as root only our own kind of config: nothing that writes files or opens control ports
      (umask 077; cp "$D/tun.json" "$D/tun-root.json") && chown 0:0 "$D/tun-root.json"
      cfg="$D/tun-root.json"
      if grep -qE '"(output|path|cache_file|external_controller|external_ui|v2ray_api|clash_api)"' "$cfg"; then
        echo "rayscope: config refused" >>"$LOG"
        break
      fi
      "$D/sing-box" run -c "$cfg" -D "$D" --disable-color </dev/null >>"$LOG" 2>&1 &
    fi
    p=$!
    state "run $tok $p"
    ( while kill -0 "$owner" 2>/dev/null && kill -0 "$p" 2>/dev/null; do sleep 1; done; kill "$p" 2>/dev/null ) &
    wait "$p"
    code=$?
    # the kernel did not let the user create the adapter: once more as root
    if [ "$mode" = user ] && [ "$code" != 0 ] && [ ! -f "$D/stopping" ] && [ $(( $(date +%s) - t0 )) -lt 10 ] &&
      grep -qiE 'operation not permitted|permission denied' "$LOG"; then
      echo "rayscope: no rights as user, sing-box as root" >>"$LOG"
      mode=root
      continue
    fi
    break
  done
  state "exit $tok $code"
}

stop_sb() {
  [ -n "$runner" ] || return 0
  : > "$D/stopping"
  p=""
  i=0
  # the session may still be starting
  while [ $i -lt 50 ]; do
    s="$(st)"
    case "$s" in "run $cur "*) p="${s##* }"; break ;; "exit $cur "*) break ;; esac
    sleep 0.1
    i=$((i+1))
  done
  if [ -n "$p" ]; then
    kill "$p" 2>/dev/null
    i=0
    while [ $i -lt 60 ]; do
      case "$(st)" in "exit $cur "*) break ;; esac
      sleep 0.1
      i=$((i+1))
    done
    s="$(st)"
    case "$s" in "run $cur "*) kill -9 "${s##* }" 2>/dev/null ;; esac
  fi
  wait "$runner" 2>/dev/null
  runner=""
  rm -f "$D/stopping"
}

cleanup() {
  stop_sb
  rm -rf "$D"
  exit 0
}
trap cleanup TERM INT HUP

state idle
echo $$ > "$D/pid.tmp" && mv -f "$D/pid.tmp" "$D/pid"
exec 4<>"$D/ctl"
while IFS= read -r line <&4; do
  case "$line" in
    "start "*)
      rest="${line#start }"
      tok="${rest%% *}"
      rest="${rest#* }"
      owner="${rest%% *}"
      json="${rest#* }"
      case "$tok" in ''|*[!A-Za-z0-9]*) continue ;; esac
      case "$owner" in ''|*[!0-9]*) continue ;; esac
      stop_sb
      rm -f "$D/tun.json" "$D/tun-root.json"
      (umask 077; printf '%s\n' "$json" > "$D/tun.json")
      [ "$MODE" = user ] && chown "$U:$G" "$D/tun.json"
      printf '# %s\n' "$tok" > "$LOG"
      cur="$tok"
      run "$tok" "$owner" </dev/null >/dev/null 2>&1 &
      runner=$!
      ;;
    "stop "*)
      # only the session RayScope means, never a newer one
      if [ "${line#stop }" = "$cur" ]; then
        stop_sb
        state "stopped $cur"
      fi
      ;;
    quit)
      cleanup
      ;;
  esac
done
cleanup
