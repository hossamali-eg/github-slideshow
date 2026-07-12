#!/usr/bin/env python3
"""
WiFi Manager Dashboard — ZTE ZXHN H188A
تشغيل: python3 app.py  →  http://localhost:8080
"""

import json, os, re, socket, subprocess, threading, time
from datetime import datetime
from flask import Flask, jsonify, render_template, request
from zte_client import ZTEClient

app = Flask(__name__)

ROUTER_IP   = os.getenv("ROUTER_IP",   "192.168.1.1")
ROUTER_USER = os.getenv("ROUTER_USER", "admin")
ROUTER_PASS = os.getenv("ROUTER_PASS", "")

zte = ZTEClient(ROUTER_IP, ROUTER_USER, ROUTER_PASS)

# ── Persistent device DB (names, speed limits, schedules) ─────────────────────
DB_FILE     = os.path.join(os.path.dirname(__file__), "devices_db.json")
CONFIG_FILE = os.path.join(os.path.dirname(__file__), "router_config.json")

def _load_db() -> dict:
    try:
        with open(DB_FILE) as f:
            return json.load(f)
    except Exception:
        return {}

def _save_db(db: dict):
    with open(DB_FILE, "w") as f:
        json.dump(db, f, ensure_ascii=False, indent=2)

def _load_config() -> dict:
    try:
        with open(CONFIG_FILE) as f:
            return json.load(f)
    except Exception:
        return {}

def _save_config(cfg: dict):
    with open(CONFIG_FILE, "w") as f:
        json.dump(cfg, f, ensure_ascii=False, indent=2)

# ── In-memory state ───────────────────────────────────────────────────────────
_blocked: set      = set()
_internet_on: bool = True
_devices_cache: list = []
_hostname_cache: dict = {}   # ip → hostname
_cache_lock = threading.Lock()

# ── Hostname resolution ───────────────────────────────────────────────────────

def _resolve_hostname(ip: str) -> str:
    if ip in _hostname_cache:
        return _hostname_cache[ip]
    name = ip
    # 1. Reverse DNS
    try:
        name = socket.gethostbyaddr(ip)[0]
    except Exception:
        pass
    # 2. NetBIOS / mDNS via nmap if available
    if name == ip:
        try:
            out = subprocess.check_output(
                ["nmap", "-sn", "-R", ip, "--system-dns"],
                text=True, timeout=4, stderr=subprocess.DEVNULL
            )
            m = re.search(r"Nmap scan report for (.+?) \(", out)
            if m:
                name = m.group(1)
        except Exception:
            pass
    _hostname_cache[ip] = name
    return name

# ── ARP scanner ───────────────────────────────────────────────────────────────

def _arp_scan() -> list:
    try:
        out = subprocess.check_output(["arp", "-a"], text=True, timeout=5)
    except Exception:
        return []

    db = _load_db()
    devices = []
    for line in out.splitlines():
        m = re.search(r"\((\d+\.\d+\.\d+\.\d+)\) at ([0-9a-fA-F:]{17})", line)
        if not m:
            continue
        ip, mac = m.group(1), m.group(2).lower()
        if mac == "ff:ff:ff:ff:ff:ff":
            continue
        info     = db.get(mac, {})
        hostname = info.get("name") or _resolve_hostname(ip)
        devices.append({
            "ip":          ip,
            "mac":         mac,
            "hostname":    hostname,
            "online":      True,
            "blocked":     mac in _blocked,
            "speed_limit": info.get("speed_limit", {"download": 0, "upload": 0}),
            "schedule":    info.get("schedule", {"enabled": False, "from": "", "to": ""}),
        })
    return devices

def _ping_sweep(subnet: str) -> None:
    for i in range(1, 255):
        subprocess.Popen(
            ["ping", "-c", "1", "-W", "1", f"{subnet}.{i}"],
            stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL
        )

# ── Schedule checker ──────────────────────────────────────────────────────────

def _check_schedules():
    """Block/unblock devices according to their saved schedule."""
    db = _load_db()
    now_str = datetime.now().strftime("%H:%M")
    h, mi = int(now_str[:2]), int(now_str[3:])
    now_min = h * 60 + mi

    for mac, info in db.items():
        sch = info.get("schedule", {})
        if not sch.get("enabled"):
            continue
        f = sch.get("from", "")
        t = sch.get("to", "")
        if not f or not t:
            continue
        try:
            fh, fm = int(f[:2]), int(f[3:])
            th, tm = int(t[:2]), int(t[3:])
        except Exception:
            continue
        from_min = fh * 60 + fm
        to_min   = th * 60 + tm

        # Handle overnight ranges (e.g. 22:00 → 07:00)
        if from_min <= to_min:
            should_block = from_min <= now_min < to_min
        else:
            should_block = now_min >= from_min or now_min < to_min

        if should_block and mac not in _blocked:
            _blocked.add(mac)
            zte.block_device(mac, True)
        elif not should_block and mac in _blocked:
            if info.get("schedule_was_blocked"):
                _blocked.discard(mac)
                zte.block_device(mac, False)

        db[mac]["schedule_was_blocked"] = should_block
    _save_db(db)

# ── Background thread ─────────────────────────────────────────────────────────

def _refresh_loop():
    subnet = ".".join(ROUTER_IP.split(".")[:3])
    while True:
        _ping_sweep(subnet)
        time.sleep(3)
        with _cache_lock:
            global _devices_cache
            _devices_cache = _arp_scan()
        _check_schedules()
        time.sleep(57)

# ── Flask routes ──────────────────────────────────────────────────────────────

@app.route("/")
def index():
    return render_template("index.html", router_ip=ROUTER_IP)


@app.route("/api/devices")
def api_devices():
    with _cache_lock:
        data = list(_devices_cache)
    if not data:
        data = _arp_scan()
    for d in data:
        d["blocked"] = d["mac"] in _blocked
    return jsonify(data)


@app.route("/api/status")
def api_status():
    global _internet_on
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
    zte.set_internet(enabled)
    _internet_on = enabled
    return jsonify({"success": True, "enabled": enabled})


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


@app.route("/api/rename", methods=["POST"])
def api_rename():
    mac  = request.json.get("mac", "").lower()
    name = request.json.get("name", "").strip()
    if not mac or not name:
        return jsonify({"success": False}), 400
    db = _load_db()
    db.setdefault(mac, {})["name"] = name
    _save_db(db)
    # Update cache immediately
    _hostname_cache[mac] = name  # won't match key type but harmless
    with _cache_lock:
        for d in _devices_cache:
            if d["mac"] == mac:
                d["hostname"] = name
    return jsonify({"success": True})


@app.route("/api/speed_limit", methods=["POST"])
def api_speed_limit():
    mac      = request.json.get("mac", "").lower()
    dl_mbps  = int(request.json.get("download", 0))
    ul_mbps  = int(request.json.get("upload",   0))
    if not mac:
        return jsonify({"success": False}), 400
    db = _load_db()
    db.setdefault(mac, {})["speed_limit"] = {"download": dl_mbps, "upload": ul_mbps}
    _save_db(db)
    # Try ZTE API (Kbps)
    zte.set_speed_limit(mac, dl_mbps * 1000, ul_mbps * 1000)
    with _cache_lock:
        for d in _devices_cache:
            if d["mac"] == mac:
                d["speed_limit"] = {"download": dl_mbps, "upload": ul_mbps}
    return jsonify({"success": True})


@app.route("/api/schedule", methods=["POST"])
def api_schedule():
    mac     = request.json.get("mac", "").lower()
    from_t  = request.json.get("from", "")
    to_t    = request.json.get("to",   "")
    enabled = bool(request.json.get("enabled", True))
    if not mac:
        return jsonify({"success": False}), 400
    db = _load_db()
    db.setdefault(mac, {})["schedule"] = {"enabled": enabled, "from": from_t, "to": to_t}
    _save_db(db)
    return jsonify({"success": True})


@app.route("/api/config")
def api_config():
    cfg = _load_config()
    return jsonify({
        "ip":       cfg.get("ip",       ROUTER_IP),
        "username": cfg.get("username", ROUTER_USER),
        "has_saved": bool(cfg.get("password")),
    })


@app.route("/api/router_login", methods=["POST"])
def api_router_login():
    data = request.json or {}
    ip   = data.get("ip",       ROUTER_IP)
    user = data.get("username", ROUTER_USER)
    pw   = data.get("password", ROUTER_PASS)
    zte.configure(ip, user, pw)
    ok = zte.login()
    if ok:
        _save_config({"ip": ip, "username": user, "password": pw})
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
    # Try auto-login: environment vars → saved config
    cfg = _load_config()
    _ip   = ROUTER_IP   or cfg.get("ip",       "192.168.1.1")
    _user = ROUTER_USER or cfg.get("username",  "admin")
    _pw   = ROUTER_PASS or cfg.get("password",  "")
    if _pw:
        zte.configure(_ip, _user, _pw)
        print(f"جاري الاتصال بالراوتر {_ip} ...")
        threading.Thread(target=zte.login, daemon=True).start()

    _devices_cache.extend(_arp_scan())
    t = threading.Thread(target=_refresh_loop, daemon=True)
    t.start()
    app.run(host="0.0.0.0", port=8080, debug=False)
