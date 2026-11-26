import asyncio, logging, os
logging.basicConfig(level=logging.DEBUG, format="%(asctime)s.%(msecs)03d %(name)s %(message)s", datefmt="%H:%M:%S")
from androidtvremote2 import AndroidTVRemote
S = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "secrets")
async def main():
    r = AndroidTVRemote("dbg", os.path.join(S, "client_cert.pem"), os.path.join(S, "client_key.pem"), "192.168.1.2")
    try:
        await asyncio.wait_for(r.async_connect(), 15)
    except Exception as e:
        print("CONNECT FAILED:", repr(e)); return
    print("CONNECTED is_on", r.is_on, flush=True)
    await asyncio.sleep(4)
    print("sending HOME", flush=True)
    r.send_key_command("HOME")
    await asyncio.sleep(3)
    r.disconnect()
asyncio.run(main())
