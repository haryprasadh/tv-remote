"""Probe the TV's remote protocol behaviour (open questions in the v1 spec). Uses ../secrets/."""
import asyncio
import os
import socket
import subprocess
import sys
import time

from androidtvremote2 import AndroidTVRemote

HERE = os.path.dirname(os.path.abspath(__file__))
CERT = os.path.join(HERE, "..", "secrets", "client_cert.pem")
KEY = os.path.join(HERE, "..", "secrets", "client_key.pem")
HOST = sys.argv[1]
WIFI_MAC = sys.argv[2]
ADB = dict(os.environ, ANDROID_SERIAL=f"{HOST}:5555")


def front():
    out = subprocess.run(["adb", "shell", "-n", "dumpsys activity activities | grep -m1 mResumedActivity"],
                         env=ADB, capture_output=True, text=True, stdin=subprocess.DEVNULL).stdout
    return out.strip().split(" ")[-2] if out.strip() else "?"


def port_open(port=6466, timeout=1.5):
    try:
        with socket.create_connection((HOST, port), timeout=timeout):
            return True
    except OSError:
        return False


def wol(mac):
    payload = bytes.fromhex("FF" * 6 + mac.replace(":", "") * 16)
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
        for addr in ("255.255.255.255", "192.168.1.255"):
            for port in (9, 7):
                s.sendto(payload, (addr, port))


async def connected_remote(name="probe"):
    r = AndroidTVRemote(name, CERT, KEY, HOST)
    await r.async_connect()
    return r


async def main():
    r = await connected_remote()
    print(f"[1] connected, is_on={r.is_on}, volume={r.volume_info}")

    t = time.time(); r.send_key_command("HOME"); await asyncio.sleep(1.5)
    print(f"[2] HOME -> front={front()}")

    r.send_launch_app_command("com.google.android.youtube.tvmusic"); await asyncio.sleep(5)
    print(f"[3] launch YT Music (market://launch) -> front={front()}, current_app={r.current_app}")
    r.send_key_command("HOME"); await asyncio.sleep(2)

    r.send_launch_app_command("https://www.youtube.com/watch?v=aqz-KE-bpKQ"); await asyncio.sleep(8)
    print(f"[4] YouTube link -> front={front()}, current_app={r.current_app}")
    r.send_key_command("HOME"); await asyncio.sleep(2)

    before = r.volume_info
    r.send_key_command("VOLUME_UP"); await asyncio.sleep(1.0); mid = r.volume_info
    r.send_key_command("VOLUME_DOWN"); await asyncio.sleep(1.0)
    print(f"[5] volume before={before} after_up={mid} after_down={r.volume_info}")

    # Two clients, same certificate
    r2 = await connected_remote("probe2"); await asyncio.sleep(3)
    print(f"[6] second client connected; first still connected? "
          f"{r._remote_message_protocol is not None and not r._remote_message_protocol.transport.is_closing()}")
    r2.send_key_command("DPAD_DOWN"); r.send_key_command("DPAD_UP"); await asyncio.sleep(1)
    print(f"    both sent keys without error")
    r2.disconnect()

    # Sleep, then standby behaviour and Wake-on-LAN
    r.send_key_command("POWER"); t0 = time.time()
    for _ in range(20):
        await asyncio.sleep(1)
        if not r.is_on:
            break
    print(f"[7] POWER -> is_on={r.is_on} after {time.time() - t0:.1f}s")
    await asyncio.sleep(8)
    print(f"    port 6466 open in sleep (8 s later)? {port_open()}")
    r.disconnect()
    await asyncio.sleep(20)
    print(f"    port 6466 open 28 s after sleep? {port_open()}")
    wol(WIFI_MAC); t1 = time.time()
    while time.time() - t1 < 60 and not port_open():
        await asyncio.sleep(1)
    print(f"[8] WoL -> port open={port_open()} after {time.time() - t1:.1f}s")
    if port_open():
        r = await connected_remote()
        print(f"    reconnected, is_on={r.is_on}")
        if not r.is_on:
            r.send_key_command("POWER"); await asyncio.sleep(4)
            print(f"    sent POWER, is_on={r.is_on}")
        r.disconnect()


if __name__ == "__main__":
    asyncio.run(main())
