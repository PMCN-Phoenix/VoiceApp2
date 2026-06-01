import asyncio
import websockets
import json

async def test():
    async with websockets.connect("ws://localhost:8080") as ws:
        await ws.send(json.dumps({"type": "start"}))
        await ws.send(json.dumps({"type": "end"}))
        result = await ws.recv()
        print("收到结果:", result)

asyncio.run(test())