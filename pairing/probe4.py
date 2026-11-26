import asyncio, logging, os, subprocess
logging.basicConfig(level=logging.WARNING)
from androidtvremote2 import AndroidTVRemote
S = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "secrets")
ENV = dict(os.environ, ANDROID_SERIAL="192.168.1.2:5555")
def front():
    o = subprocess.run(["adb","shell","-n","dumpsys activity activities | grep -m1 mResumedActivity"], env=ENV, capture_output=True, text=True, stdin=subprocess.DEVNULL).stdout.split()
    return o[-2] if len(o) > 2 else "?"
def mk(n): return AndroidTVRemote(n, os.path.join(S,"client_cert.pem"), os.path.join(S,"client_key.pem"), "192.168.1.2")
async def main():
    r = mk("p4"); await asyncio.wait_for(r.async_connect(), 15)
    r.send_launch_app_command("com.google.android.youtube.tvmusic"); await asyncio.sleep(6)
    print("market://launch YT Music ->", front(), flush=True)
    r.send_key_command("HOME"); await asyncio.sleep(2)
    avail = []; r.add_is_available_updated_callback(lambda a: avail.append(a))
    r2 = mk("p5"); await asyncio.wait_for(r2.async_connect(), 15); await asyncio.sleep(4)
    r.send_key_command("DPAD_RIGHT"); await asyncio.sleep(0.5); r2.send_key_command("DPAD_LEFT"); await asyncio.sleep(2)
    print("two clients: first client's availability events:", avail, flush=True)
    r2.disconnect(); r.disconnect()
asyncio.run(main())
