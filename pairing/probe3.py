import asyncio, logging, os, subprocess
logging.basicConfig(level=logging.WARNING)
from androidtvremote2 import AndroidTVRemote
S = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "secrets")
ENV = dict(os.environ, ANDROID_SERIAL="192.168.1.2:5555")
def front():
    o = subprocess.run(["adb","shell","-n","dumpsys activity activities | grep -m1 mResumedActivity"], env=ENV, capture_output=True, text=True, stdin=subprocess.DEVNULL).stdout.split()
    return o[-2] if len(o) > 2 else "?"
async def main():
    r = AndroidTVRemote("p3", os.path.join(S,"client_cert.pem"), os.path.join(S,"client_key.pem"), "192.168.1.2")
    await asyncio.wait_for(r.async_connect(), 15)
    vols = []; r.add_volume_info_updated_callback(lambda v: vols.append(v["level"]))
    r.send_key_command("VOLUME_UP"); await asyncio.sleep(1.5); r.send_key_command("VOLUME_DOWN"); await asyncio.sleep(1.5)
    print("VOLUME levels reported:", vols, flush=True)
    for link in ["https://www.youtube.com/watch?v=aqz-KE-bpKQ", "https://music.youtube.com", "https://www.netflix.com/title/80100172"]:
        r.send_launch_app_command(link); await asyncio.sleep(6)
        print(f"{link} -> {front()}", flush=True)
        r.send_key_command("HOME"); await asyncio.sleep(2)
    r.disconnect()
asyncio.run(main())
