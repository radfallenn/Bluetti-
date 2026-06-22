import asyncio
import json
import time
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Dict, List, Optional

import yaml
from bleak import BleakClient
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse, JSONResponse
from fastapi.middleware.cors import CORSMiddleware
import uvicorn

try:
    import paho.mqtt.client as mqtt
except Exception:
    mqtt = None

CONFIG_PATH = Path("config.yaml")
DATA_DIR = Path("data")
DATA_DIR.mkdir(exist_ok=True)

FF_NOTIFY = "0000ff01-0000-1000-8000-00805f9b34fb"
FF_WRITE = "0000ff02-0000-1000-8000-00805f9b34fb"


def load_config() -> Dict[str, Any]:
    with open(CONFIG_PATH, "r", encoding="utf-8") as f:
        return yaml.safe_load(f)


def crc16(data: bytes) -> int:
    crc = 0xFFFF
    for b in data:
        crc ^= b
        for _ in range(8):
            crc = ((crc >> 1) ^ 0xA001) if (crc & 1) else (crc >> 1)
    return crc & 0xFFFF


def with_crc(body: bytes) -> bytes:
    c = crc16(body)
    return body + bytes([c & 0xFF, (c >> 8) & 0xFF])


def hexs(data: bytes) -> str:
    return " ".join(f"{b:02X}" for b in data)


@dataclass
class BluettiState:
    id: str
    name: str
    model: str
    mac: str
    online: bool = False
    connected: bool = False
    ready: bool = False
    battery: Optional[int] = None
    ac_input_w: int = 0
    dc_input_w: int = 0
    input_total_w: int = 0
    ac_output_w: int = 0
    dc_output_w: int = 0
    output_total_w: int = 0
    ac_enabled: Optional[bool] = None
    dc_enabled: Optional[bool] = None
    temperature: Optional[float] = None
    remaining_minutes: Optional[int] = None
    last_error: str = ""
    last_rx_hex: str = ""
    last_tx_hex: str = ""
    updated_at: float = field(default_factory=time.time)
    history: List[Dict[str, Any]] = field(default_factory=list)

    def as_dict(self) -> Dict[str, Any]:
        return {
            "id": self.id,
            "name": self.name,
            "model": self.model,
            "mac": self.mac,
            "online": self.online,
            "connected": self.connected,
            "ready": self.ready,
            "battery": self.battery,
            "ac_input_w": self.ac_input_w,
            "dc_input_w": self.dc_input_w,
            "input_total_w": self.input_total_w,
            "ac_output_w": self.ac_output_w,
            "dc_output_w": self.dc_output_w,
            "output_total_w": self.output_total_w,
            "ac_enabled": self.ac_enabled,
            "dc_enabled": self.dc_enabled,
            "temperature": self.temperature,
            "remaining_minutes": self.remaining_minutes,
            "last_error": self.last_error,
            "last_rx_hex": self.last_rx_hex,
            "last_tx_hex": self.last_tx_hex,
            "updated_at": self.updated_at,
            "updated_at_iso": time.strftime("%Y-%m-%dT%H:%M:%S", time.localtime(self.updated_at)),
            "history": self.history[-120:],
        }


class MqttBridge:
    def __init__(self, cfg: Dict[str, Any]):
        self.cfg = cfg
        self.client = None
        self.base = cfg.get("base_topic", "bluetti")
        self.enabled = bool(cfg.get("enabled", False)) and mqtt is not None

    def start(self):
        if not self.enabled:
            return
        try:
            self.client = mqtt.Client(mqtt.CallbackAPIVersion.VERSION2)
            username = self.cfg.get("username") or ""
            password = self.cfg.get("password") or ""
            if username:
                self.client.username_pw_set(username, password)
            self.client.connect(self.cfg.get("host", "127.0.0.1"), int(self.cfg.get("port", 1883)), 30)
            self.client.loop_start()
        except Exception:
            self.client = None

    def publish_state(self, state: BluettiState):
        if not self.client:
            return
        topic = f"{self.base}/{state.id}"
        data = state.as_dict()
        try:
            self.client.publish(f"{topic}/status", json.dumps(data), retain=True)
            self.client.publish(f"{topic}/battery", state.battery if state.battery is not None else "", retain=True)
            self.client.publish(f"{topic}/input_total", state.input_total_w, retain=True)
            self.client.publish(f"{topic}/output_total", state.output_total_w, retain=True)
            self.client.publish(f"{topic}/ac", json.dumps(state.ac_enabled), retain=True)
            self.client.publish(f"{topic}/dc", json.dumps(state.dc_enabled), retain=True)
            self.client.publish(f"{topic}/online", json.dumps(state.online), retain=True)
        except Exception:
            pass


class BluettiDevice:
    def __init__(self, cfg: Dict[str, Any], bridge: "Bridge"):
        self.cfg = cfg
        self.bridge = bridge
        self.state = BluettiState(
            id=cfg["id"], name=cfg.get("name", cfg["id"]), model=cfg.get("model", "Bluetti"), mac=cfg["mac"]
        )
        self.client: Optional[BleakClient] = None
        self.write_char: Optional[str] = None
        self.notify_char: Optional[str] = None
        self.buffer = bytearray()
        self.running = True
        self.last_command_at = 0.0

    async def loop(self):
        while self.running:
            try:
                if not self.client or not self.client.is_connected:
                    await self.connect()
                if self.client and self.client.is_connected and self.write_char:
                    await self.read_status()
                    await self.run_automation()
                await asyncio.sleep(float(self.bridge.config["bridge"].get("read_interval_seconds", 5)))
            except Exception as e:
                self.state.last_error = str(e)
                self.state.online = False
                await self.disconnect()
                await self.bridge.broadcast()
                await asyncio.sleep(float(self.bridge.config["bridge"].get("reconnect_interval_seconds", 10)))

    async def connect(self):
        self.state.last_error = ""
        self.state.connected = False
        self.state.ready = False
        self.client = BleakClient(self.state.mac, timeout=20)
        await self.client.connect()
        self.state.connected = True
        await self.discover_channels()
        if self.notify_char:
            await self.client.start_notify(self.notify_char, self.on_notify)
        self.state.ready = bool(self.write_char)
        self.state.online = self.state.ready
        await self.bridge.broadcast()

    async def disconnect(self):
        try:
            if self.client:
                await self.client.disconnect()
        except Exception:
            pass
        self.client = None
        self.state.connected = False
        self.state.ready = False
        self.state.online = False

    async def discover_channels(self):
        self.write_char = None
        self.notify_char = None
        services = self.client.services
        candidates = []
        for service in services:
            for char in service.characteristics:
                props = set(char.properties or [])
                uuid = str(char.uuid).lower()
                candidates.append({"uuid": uuid, "props": list(props)})
                if uuid == FF_NOTIFY:
                    self.notify_char = str(char.uuid)
                if uuid == FF_WRITE:
                    self.write_char = str(char.uuid)
        if not self.notify_char:
            for service in services:
                for char in service.characteristics:
                    props = set(char.properties or [])
                    uuid = str(char.uuid).lower()
                    if "notify" in props or "indicate" in props:
                        self.notify_char = str(char.uuid)
                        if "ff01" in uuid or "ffe1" in uuid:
                            break
                if self.notify_char:
                    break
        if not self.write_char:
            for service in services:
                for char in service.characteristics:
                    props = set(char.properties or [])
                    uuid = str(char.uuid).lower()
                    if "write" in props or "write-without-response" in props:
                        self.write_char = str(char.uuid)
                        if "ff02" in uuid or "ffe2" in uuid:
                            break
                if self.write_char:
                    break
        self.state.last_error = "channels=" + json.dumps(candidates[-20:])

    async def send(self, body: bytes):
        if not self.client or not self.client.is_connected or not self.write_char:
            raise RuntimeError("BLE write channel unavailable")
        pkt = with_crc(body)
        self.state.last_tx_hex = hexs(pkt)
        await self.client.write_gatt_char(self.write_char, pkt, response=False)

    async def send_read(self, address: int, qty: int):
        await self.send(bytes([1, 3, (address >> 8) & 0xFF, address & 0xFF, (qty >> 8) & 0xFF, qty & 0xFF]))

    async def write_register(self, address: int, value: int):
        await self.send(bytes([1, 6, (address >> 8) & 0xFF, address & 0xFF, (value >> 8) & 0xFF, value & 0xFF]))

    async def read_status(self):
        model = self.state.model.upper()
        if "AC180" in model:
            await self.send_read(36, 16)
            await asyncio.sleep(0.6)
            await self.send_read(0, 32)
            await asyncio.sleep(0.6)
            await self.send_read(100, 32)
            await asyncio.sleep(0.6)
            await self.send_read(3007, 2)
        else:
            await self.send_read(36, 8)
            await asyncio.sleep(0.7)
            await self.send_read(3007, 2)

    def on_notify(self, _sender, data: bytearray):
        raw = bytes(data)
        self.state.last_rx_hex = hexs(raw)
        self.buffer.extend(raw)
        self.parse_buffer()

    def parse_buffer(self):
        if len(self.buffer) > 512:
            self.buffer.clear()
            return
        if len(self.buffer) < 5:
            return
        cur = bytes(self.buffer)
        if cur[0] == 1 and cur[1] == 3:
            byte_count = cur[2]
            total = byte_count + 5
            if len(cur) >= total:
                pkt = cur[:total]
                self.buffer = bytearray(cur[total:])
                if crc16(pkt[:-2]) == int.from_bytes(pkt[-2:], "little"):
                    self.parse_read(pkt)
                return
        if len(cur) >= 8 and cur[0] == 1 and cur[1] == 6:
            self.buffer = bytearray(cur[8:])
            return
        if len(cur) > 64:
            self.buffer.clear()

    def parse_read(self, pkt: bytes):
        try:
            byte_count = pkt[2]
            words = []
            for i in range(byte_count // 2):
                words.append((pkt[3 + i * 2] << 8) | pkt[4 + i * 2])
            if len(words) >= 8:
                self.state.dc_input_w = words[0]
                self.state.ac_input_w = words[1]
                self.state.ac_output_w = words[2]
                self.state.dc_output_w = words[3]
                self.state.battery = words[7]
            elif len(words) == 2:
                self.state.ac_enabled = words[0] == 1
                self.state.dc_enabled = words[1] == 1
            self.state.input_total_w = int(self.state.ac_input_w or 0) + int(self.state.dc_input_w or 0)
            self.state.output_total_w = int(self.state.ac_output_w or 0) + int(self.state.dc_output_w or 0)
            self.state.online = True
            self.state.connected = True
            self.state.ready = True
            self.state.updated_at = time.time()
            self.state.history.append({
                "t": self.state.updated_at,
                "battery": self.state.battery,
                "input": self.state.input_total_w,
                "output": self.state.output_total_w,
            })
            max_points = int(self.bridge.config["bridge"].get("history_max_points", 720))
            self.state.history = self.state.history[-max_points:]
            asyncio.create_task(self.bridge.broadcast())
        except Exception as e:
            self.state.last_error = f"parse error: {e}"

    async def command(self, command: str):
        if command == "read":
            await self.read_status()
        elif command == "ac_on":
            await self.write_register(3007, 1)
        elif command == "ac_off":
            await self.write_register(3007, 0)
        elif command == "dc_on":
            await self.write_register(3008, 1)
        elif command == "dc_off":
            await self.write_register(3008, 0)
        else:
            raise ValueError("unknown command")

    async def run_automation(self):
        cfg = self.bridge.config.get("automation", {})
        if not cfg.get("enabled") or self.state.battery is None:
            return
        now = time.time()
        if now - self.last_command_at < float(cfg.get("cooldown_seconds", 60)):
            return
        b = self.state.battery
        if b <= int(cfg.get("ac_off_below", 20)):
            await self.command("ac_off")
            self.last_command_at = now
        if b <= int(cfg.get("dc_off_below", 15)):
            await self.command("dc_off")
            self.last_command_at = now
        if b >= int(cfg.get("ac_on_above", 80)):
            await self.command("ac_on")
            self.last_command_at = now
        if b >= int(cfg.get("dc_on_above", 75)):
            await self.command("dc_on")
            self.last_command_at = now


class Bridge:
    def __init__(self):
        self.config = load_config()
        self.devices: Dict[str, BluettiDevice] = {}
        self.websockets: List[WebSocket] = []
        self.mqtt = MqttBridge(self.config.get("mqtt", {}))

    async def start(self):
        self.mqtt.start()
        for d in self.config.get("devices", []):
            if d.get("enabled", True):
                dev = BluettiDevice(d, self)
                self.devices[dev.state.id] = dev
                asyncio.create_task(dev.loop())

    async def broadcast(self):
        payload = self.status()
        for dev in self.devices.values():
            self.mqtt.publish_state(dev.state)
        dead = []
        for ws in self.websockets:
            try:
                await ws.send_json(payload)
            except Exception:
                dead.append(ws)
        for ws in dead:
            if ws in self.websockets:
                self.websockets.remove(ws)

    def status(self):
        return {"devices": {k: d.state.as_dict() for k, d in self.devices.items()}, "automation": self.config.get("automation", {})}


bridge = Bridge()
app = FastAPI(title="Bluetti Bridge", version="1.0.0")
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


@app.on_event("startup")
async def startup():
    await bridge.start()


@app.get("/")
async def dashboard():
    return HTMLResponse(Path("web/index.html").read_text(encoding="utf-8"))


@app.get("/api/status")
async def api_status():
    return bridge.status()


@app.get("/api/devices/{device_id}/status")
async def api_device_status(device_id: str):
    dev = bridge.devices.get(device_id)
    if not dev:
        return JSONResponse({"error": "device not found"}, status_code=404)
    return dev.state.as_dict()


@app.post("/api/devices/{device_id}/{target}/{action}")
async def api_command(device_id: str, target: str, action: str):
    dev = bridge.devices.get(device_id)
    if not dev:
        return JSONResponse({"error": "device not found"}, status_code=404)
    cmd = f"{target}_{action}" if target in ["ac", "dc"] else action
    await dev.command(cmd)
    return {"ok": True, "command": cmd}


@app.post("/api/devices/{device_id}/read")
async def api_read(device_id: str):
    dev = bridge.devices.get(device_id)
    if not dev:
        return JSONResponse({"error": "device not found"}, status_code=404)
    await dev.read_status()
    return {"ok": True}


@app.get("/api/automation")
async def get_automation():
    return bridge.config.get("automation", {})


@app.post("/api/automation")
async def set_automation(payload: Dict[str, Any]):
    bridge.config["automation"].update(payload)
    return bridge.config["automation"]


@app.websocket("/ws")
async def ws_endpoint(ws: WebSocket):
    await ws.accept()
    bridge.websockets.append(ws)
    await ws.send_json(bridge.status())
    try:
        while True:
            await ws.receive_text()
    except WebSocketDisconnect:
        if ws in bridge.websockets:
            bridge.websockets.remove(ws)


if __name__ == "__main__":
    cfg = load_config()["bridge"]
    uvicorn.run(app, host=cfg.get("host", "0.0.0.0"), port=int(cfg.get("port", 5055)))
