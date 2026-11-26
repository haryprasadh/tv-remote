import asyncio, logging, os
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(message)s", datefmt="%H:%M:%S")
logging.getLogger("androidtvremote2").setLevel(logging.WARNING)
from androidtvremote2 import AndroidTVRemote
S = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "secrets")
C, K = os.path.join(S, "client_cert.pem"), os.path.join(S, "client_key.pem")
async def remote(name):
    r = AndroidTVRemote(name, C, K, "192.168.1.2"); await asyncio.wait_for(r.async_connect(), 15); return r
async def main():
    r = await remote("p1")
    apps = []
    r.add_current_app_updated_callback(lambda a: apps.append(a))
    vols = []
    r.add_volume_info_updated_callback(lambda v: vols.append(v))
    for link in ["market://launch?id=com.google.android.youtube.tvmusic",
                 "https://music.youtube.com",
                 "https://www.youtube.com/watch?v=aqz-KE-bpKQ"]:
        apps.clear(); r.send_launch_app_command(link); await asyncio.sleep(4)
        print(f"LINK {link} -> apps seen {apps}", flush=True)
        r.send_key_command("HOME"); await asyncio.sleep(1.5)
    vols.clear(); r.send_key_command("VOLUME_UP"); await asyncio.sleep(1.2); r.send_key_command("VOLUME_DOWN"); await asyncio.sleep(1.2)
    print(f"VOLUME updates: {[v['level'] for v in vols]}", flush=True)
    lost = []
    r.add_is_available_updated_callback(lambda a: lost.append(a))
    r2 = await remote("p2"); await asyncio.sleep(3)
    print(f"TWO CLIENTS: first client availability changes {lost}; is r2 on? {r2.is_on}", flush=True)
    r2.disconnect(); r.disconnect()
asyncio.run(main())
