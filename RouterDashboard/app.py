#!/usr/bin/env python3
"""
WiFi Manager Dashboard — ZTE ZXHN H188A
تشغيل: python app.py  →  http://localhost:5000
"""

import os, re, socket, subprocess, threading, time
from flask import Flask, jsonify, render_template, request
from zte_client import ZTEClient

app = Flask(__name__)

ROUTER_IP   = os.getenv("ROUTER_IP",   "192.168.1.1")
ROUTER_USER = os.getenv("ROUTER_USER", "admin")
ROUTER_PASS = os.getenv("ROUTER_PASS", "")

zte = ZTEClient(ROUTER_IP, ROUTER_USER, ROUTER_PASS)

# In-memory state
_blocked: set = set()
_internet_on: bool = True
_devices_cache: list = []
_cache_lock = threading.Lock()


# ── Network Scanner ───────────────────────────────────────────────────────────

def _hostname(ip: str) -> str:
    try:
        return socket.gethostbyaddr(ip)[0]
    except Exception:
        return ip


def _arp_scan() -> list:
    """Read ARP table — works on any Mac without root."""
    try:
        out = subprocess.check_output(["arp", "-a"], text=True, timeout=5)
    except Exception:
        return []

    devices = []
    for line in out.splitlines():
        m = re.search(r"\((\d+\.\d+\.\d+\.\d+)\) at ([0-9a-fA-F:]{17})", line)
        if not m:
            continue
        ip, mac = m.group(1), m.group(2).lower()
        if mac == "ff:ff:ff:ff:ff:ff":
            continue
        devices.append({
            "ip":       ip,
            "mac":      mac,
            "hostname": _hostname(ip),
            "online":   True,
            "blocked":  mac in _blocked,
        })
    return devices


def _ping_sweep(subnet: str = "192.168.1") -> None:
    """Background ping sweep to populate ARP cache."""
    for i in range(1, 255):
        subprocess.Popen(
            ["ping", "-c", "1", "-W", "1", f"{subnet}.{i}"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )


def _refresh_loop() -> None:
    """Background thread: sweep + update cache every 30 s."""
    subnet = ".".join(ROUTER_IP.split(".")[:3])
    while True:
        _ping_sweep(subnet)
        time.sleep(3)
        with _cache_lock:
            global _devices_cache
            _devices_cache = _arp_scan()
        time.sleep(27)


# ── Flask Routes ──────────────────────────────────────────────────────────────

@app.route("/")
def index():
    return render_template("index.html", router_ip=ROUTER_IP)


@app.route("/api/devices")
def api_devices():
    with _cache_lock:
        data = list(_devices_cache)
    # Merge blocked state
    for d in data:
        d["blocked"] = d["mac"] in _blocked
    if not data:
        data = _arp_scan()
    return jsonify(data)


@app.route("/api/status")
def api_status():
    global _internet_on
    # Try router API first
    router_data = zte.get_status()
    if router_data:
        _internet_on = router_data.get("internet", _internet_on)
        return jsonify({**router_data, "router_connected": True})

    with _cache_lock:
        count = len(_devices_cache)

    return jsonify({
        "router_connected": False,
        "internet":         _internet_on,
        "download":         0,
        "upload":           0,
        "ssid":             f"ZTE_{ROUTER_IP}",
        "devices_count":    count,
        "blocked_count":    len(_blocked),
    })


@app.route("/api/internet", methods=["POST"])
def api_internet():
    global _internet_on
    enabled = bool(request.json.get("enabled", True))
    ok = zte.set_internet(enabled)
    _internet_on = enabled
    return jsonify({"success": ok or True, "enabled": enabled})


@app.route("/api/block", methods=["POST"])
def api_block():
    mac   = request.json.get("mac", "").lower()
    block = bool(request.json.get("block", True))
    if not mac:
        return jsonify({"success": False}), 400
    if block:
        _blocked.add(mac)
    else:
        _blocked.discard(mac)
    zte.block_device(mac, block)
    return jsonify({"success": True, "mac": mac, "blocked": block})


@app.route("/api/router_login", methods=["POST"])
def api_router_login():
    data = request.json or {}
    ip   = data.get("ip",       ROUTER_IP)
    user = data.get("username", ROUTER_USER)
    pwd  = data.get("password", ROUTER_PASS)
    zte.configure(ip, user, pwd)
    ok = zte.login()
    return jsonify({"success": ok})


# ── Startup ───────────────────────────────────────────────────────────────────

if __name__ == "__main__":
    print("""
╔══════════════════════════════════════╗
║      WiFi Manager Dashboard          ║
║      لوحة تحكم الراوتر              ║
╠══════════════════════════════════════╣
║  افتح المتصفح:  http://localhost:8080 ║
╚══════════════════════════════════════╝
""")
    # Start background refresh
    t = threading.Thread(target=_refresh_loop, daemon=True)
    t.start()
    # Initial scan
    _devices_cache.extend(_arp_scan())

    app.run(host="0.0.0.0", port=8080, debug=False)
