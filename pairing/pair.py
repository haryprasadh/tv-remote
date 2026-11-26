"""One-time pairing with the TV's Android TV Remote service, with no typing.

Starts pairing, reads the 6-hex-character code off the TV screen through adb (uiautomator), finishes
pairing, verifies a connection, then writes everything the app needs to ../secrets/:
  client_cert.pem, client_key.pem   the trusted client identity (keep private)
  client.p12                        same identity as PKCS#12 for Android (password in P12_PASSWORD)
  server_sha256.txt                 the TV's certificate fingerprint, pinned by the app

Usage: venv/bin/python -I pair.py <tv-ip> [adb-serial]
"""
import asyncio
import hashlib
import os
import re
import ssl
import subprocess
import sys
import time

from androidtvremote2 import AndroidTVRemote

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "secrets")
CERT = os.path.join(OUT, "client_cert.pem")
KEY = os.path.join(OUT, "client_key.pem")
P12 = os.path.join(OUT, "client.p12")
P12_PASSWORD = "tvremote"  # not a secret; the file itself must stay private
CODE_RE = re.compile(r'text="([0-9A-Fa-f]{6})"')


def read_code_from_tv(serial, timeout=20.0):
    """Poll the TV's UI tree until a 6-hex-character text node (the pairing code) appears."""
    env = dict(os.environ, ANDROID_SERIAL=serial)
    end = time.time() + timeout
    while time.time() < end:
        subprocess.run(["adb", "shell", "-n", "uiautomator", "dump", "/sdcard/pair-ui.xml"],
                       env=env, capture_output=True, stdin=subprocess.DEVNULL)
        xml = subprocess.run(["adb", "shell", "-n", "cat", "/sdcard/pair-ui.xml"],
                             env=env, capture_output=True, text=True, stdin=subprocess.DEVNULL).stdout
        m = CODE_RE.search(xml)
        if m:
            return m.group(1).upper()
        time.sleep(1.0)
    return None


def server_fingerprint(host, port=6466):
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    ctx.load_cert_chain(CERT, KEY)
    import socket
    with socket.create_connection((host, port), timeout=10) as raw:
        with ctx.wrap_socket(raw) as tls:
            der = tls.getpeercert(binary_form=True)
    return hashlib.sha256(der).hexdigest()


async def main():
    host = sys.argv[1]
    serial = sys.argv[2] if len(sys.argv) > 2 else f"{host}:5555"
    os.makedirs(OUT, exist_ok=True)
    remote = AndroidTVRemote("Family TV Remote", CERT, KEY, host)
    if await remote.async_generate_cert_if_missing():
        print("generated new client certificate")
    name, mac = await remote.async_get_name_and_mac()
    print(f"TV: {name}  MAC: {mac}")
    await remote.async_start_pairing()
    print("pairing started, reading code from the TV screen…")
    code = await asyncio.to_thread(read_code_from_tv, serial)
    if not code:
        code = input("couldn't read the code; type the code shown on the TV: ").strip().upper()
    print(f"code: {code}")
    await remote.async_finish_pairing(code)
    print("paired")
    await remote.async_connect()
    print(f"connected: is_on={remote.is_on} device={remote.device_info} app={remote.current_app}")
    remote.disconnect()
    subprocess.run(["openssl", "pkcs12", "-export", "-in", CERT, "-inkey", KEY, "-out", P12,
                    "-passout", f"pass:{P12_PASSWORD}", "-name", "tvremote"], check=True)
    fp = server_fingerprint(host)
    with open(os.path.join(OUT, "server_sha256.txt"), "w") as f:
        f.write(fp + "\n")
    with open(os.path.join(OUT, "tv.txt"), "w") as f:
        f.write(f"name={name}\nmac={mac}\nhost={host}\n")
    print(f"wrote {P12} and server fingerprint {fp[:16]}…")


if __name__ == "__main__":
    asyncio.run(main())
