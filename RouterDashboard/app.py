#!/usr/bin/env python3
"""
WiFi Manager Dashboard — ZTE ZXHN H188A
تشغيل: python3 app.py  →  http://localhost:8080
"""

import json, os, re, socket, subprocess, threading, time
from datetime import datetime
from flask import Flask, jsonify, render_template, request
from zte_client import ZTEClient

# ── OUI vendor database (first 3 MAC bytes → vendor, device_type) ─────────────
_OUI = {
    # Apple — iPhone/iPad
    '04:54:53':('Apple','iphone'),'0c:74:c2':('Apple','iphone'),
    '18:9e:fc':('Apple','iphone'),'20:c9:d0':('Apple','iphone'),
    '3c:07:54':('Apple','iphone'),'38:71:de':('Apple','iphone'),
    '48:d7:05':('Apple','iphone'),'4c:32:75':('Apple','iphone'),
    '50:de:06':('Apple','iphone'),'54:4e:90':('Apple','iphone'),
    '60:69:44':('Apple','iphone'),'64:a5:c3':('Apple','iphone'),
    '68:5b:35':('Apple','iphone'),'70:cd:60':('Apple','iphone'),
    '84:a1:34':('Apple','iphone'),'8c:7c:92':('Apple','iphone'),
    '8c:8e:f2':('Apple','iphone'),'90:b0:ed':('Apple','iphone'),
    '94:e9:6a':('Apple','iphone'),'98:01:a7':('Apple','iphone'),
    '9c:20:7b':('Apple','iphone'),'9c:f3:87':('Apple','iphone'),
    'a4:5e:60':('Apple','iphone'),'ac:37:43':('Apple','iphone'),
    'bc:52:b7':('Apple','iphone'),'c8:69:cd':('Apple','iphone'),
    'd0:a6:37':('Apple','iphone'),'d8:96:95':('Apple','iphone'),
    'e4:e4:ab':('Apple','iphone'),'f0:18:98':('Apple','iphone'),
    'f0:b4:79':('Apple','iphone'),'3c:d0:f8':('Apple','iphone'),
    '9c:35:eb':('Apple','iphone'),'a8:96:8a':('Apple','iphone'),
    'dc:d3:08':('Apple','iphone'),'f0:d1:a9':('Apple','iphone'),
    # Apple — Mac
    '00:1b:63':('Apple','mac'),'00:26:bb':('Apple','mac'),
    '08:6d:41':('Apple','mac'),'0c:30:21':('Apple','mac'),
    '10:40:f3':('Apple','mac'),'14:8f:c6':('Apple','mac'),
    '18:65:90':('Apple','mac'),'1c:36:bb':('Apple','mac'),
    '24:a0:74':('Apple','mac'),'28:0b:5c':('Apple','mac'),
    '34:36:3b':('Apple','mac'),'38:48:4c':('Apple','mac'),
    '40:d3:2d':('Apple','mac'),'44:00:10':('Apple','mac'),
    '44:2a:60':('Apple','mac'),'58:7f:57':('Apple','mac'),
    '5c:59:48':('Apple','mac'),'60:03:08':('Apple','mac'),
    '60:92:17':('Apple','mac'),'70:df:2f':('Apple','mac'),
    '74:1b:b2':('Apple','mac'),'74:e1:b6':('Apple','mac'),
    '78:4f:43':('Apple','mac'),'7c:f0:5f':('Apple','mac'),
    '80:d6:05':('Apple','mac'),'84:38:35':('Apple','mac'),
    '84:85:06':('Apple','mac'),'88:63:df':('Apple','mac'),
    '88:66:5a':('Apple','mac'),'8c:2d:aa':('Apple','mac'),
    '8c:85:90':('Apple','mac'),'90:72:40':('Apple','mac'),
    '90:84:0d':('Apple','mac'),'94:bf:2d':('Apple','mac'),
    '9c:04:eb':('Apple','mac'),'a8:51:ab':('Apple','mac'),
    'a8:5c:2c':('Apple','mac'),'a8:66:7f':('Apple','mac'),
    'a8:86:dd':('Apple','mac'),'ac:87:a3':('Apple','mac'),
    'b0:70:2d':('Apple','mac'),'b4:f0:ab':('Apple','mac'),
    'b8:09:8a':('Apple','mac'),'b8:78:2e':('Apple','mac'),
    'bc:3b:af':('Apple','mac'),'c4:2c:03':('Apple','mac'),
    'd0:03:4b':('Apple','mac'),'d0:23:db':('Apple','mac'),
    'd0:33:11':('Apple','mac'),'d8:00:4d':('Apple','mac'),
    'dc:2b:61':('Apple','mac'),'dc:a4:ca':('Apple','mac'),
    'e0:5f:45':('Apple','mac'),'e4:25:e7':('Apple','mac'),
    'e8:04:0b':('Apple','mac'),'f4:37:b7':('Apple','mac'),
    'f8:27:93':('Apple','mac'),'fc:a1:3e':('Apple','mac'),
    # Samsung — Phone
    '00:12:47':('Samsung','phone'),'00:15:99':('Samsung','phone'),
    '08:d4:0c':('Samsung','phone'),'14:89:fd':('Samsung','phone'),
    '18:3f:47':('Samsung','phone'),'2c:ae:2b':('Samsung','phone'),
    '3c:62:00':('Samsung','phone'),'44:4e:6d':('Samsung','phone'),
    '48:13:7e':('Samsung','phone'),'4c:a5:6d':('Samsung','phone'),
    '54:92:be':('Samsung','phone'),'58:ef:68':('Samsung','phone'),
    '60:d0:a9':('Samsung','phone'),'68:eb:c5':('Samsung','phone'),
    '70:f9:27':('Samsung','phone'),'74:45:8a':('Samsung','phone'),
    '84:cf:bf':('Samsung','phone'),'88:32:9b':('Samsung','phone'),
    '8c:a9:82':('Samsung','phone'),'94:35:0a':('Samsung','phone'),
    '94:63:d1':('Samsung','phone'),'98:39:8e':('Samsung','phone'),
    'a0:0b:ba':('Samsung','phone'),'a4:84:31':('Samsung','phone'),
    'b0:72:bf':('Samsung','phone'),'b4:3a:28':('Samsung','phone'),
    'bc:14:85':('Samsung','phone'),'c0:bd:d1':('Samsung','phone'),
    'cc:07:ab':('Samsung','phone'),'d4:87:d8':('Samsung','phone'),
    'e4:92:fb':('Samsung','phone'),'ec:9b:f3':('Samsung','phone'),
    'f0:72:8c':('Samsung','phone'),'f4:7b:5e':('Samsung','phone'),
    'f8:04:2e':('Samsung','phone'),'fc:db:b3':('Samsung','phone'),
    'fc:f1:36':('Samsung','phone'),'d0:d7:83':('Samsung','phone'),
    '78:ab:bb':('Samsung','phone'),'7c:0b:c6':('Samsung','phone'),
    # Samsung — TV
    '10:1d:c0':('Samsung','tv'),'20:9b:cd':('Samsung','tv'),
    '40:0e:85':('Samsung','tv'),'50:32:37':('Samsung','tv'),
    '84:38:38':('Samsung','tv'),'d8:31:cf':('Samsung','tv'),
    'dc:71:96':('Samsung','tv'),
    # Huawei / Honor
    '00:9a:cd':('Huawei','phone'),'04:bd:70':('Huawei','phone'),
    '04:f9:38':('Huawei','phone'),'10:1b:54':('Huawei','phone'),
    '14:a5:1a':('Huawei','phone'),'20:f3:a3':('Huawei','phone'),
    '28:3c:e4':('Huawei','phone'),'2c:cf:67':('Huawei','phone'),
    '34:6b:d3':('Huawei','phone'),'40:4d:8e':('Huawei','phone'),
    '40:cb:a8':('Huawei','phone'),'44:6e:e5':('Huawei','phone'),
    '48:db:50':('Huawei','phone'),'4c:54:99':('Huawei','phone'),
    '5c:c3:07':('Huawei','phone'),'64:16:f0':('Huawei','phone'),
    '68:13:24':('Huawei','phone'),'70:72:3c':('Huawei','phone'),
    '78:d7:52':('Huawei','phone'),'80:fb:06':('Huawei','phone'),
    '84:be:52':('Huawei','phone'),'88:e3:ab':('Huawei','phone'),
    '8c:34:fd':('Huawei','phone'),'90:17:ac':('Huawei','phone'),
    '94:04:9c':('Huawei','phone'),'94:77:2b':('Huawei','phone'),
    '98:6c:f5':('Huawei','phone'),'a4:50:46':('Huawei','phone'),
    'a4:99:47':('Huawei','phone'),'ac:e2:15':('Huawei','phone'),
    'b0:e5:ed':('Huawei','phone'),'bc:9c:31':('Huawei','phone'),
    'c0:70:4a':('Huawei','phone'),'c8:94:02':('Huawei','phone'),
    'cc:96:a0':('Huawei','phone'),'d0:7a:b5':('Huawei','phone'),
    'd4:20:b0':('Huawei','phone'),'e0:19:1d':('Huawei','phone'),
    'e4:02:9b':('Huawei','phone'),'e8:4d:d0':('Huawei','phone'),
    'f0:79:59':('Huawei','phone'),'f4:9f:54':('Huawei','phone'),
    'fc:48:ef':('Huawei','phone'),'1c:8e:5c':('Huawei','phone'),
    # Xiaomi / Redmi
    '00:9e:c8':('Xiaomi','phone'),'04:cf:8c':('Xiaomi','phone'),
    '0c:1d:af':('Xiaomi','phone'),'14:f6:5a':('Xiaomi','phone'),
    '20:82:c0':('Xiaomi','phone'),'28:6c:07':('Xiaomi','phone'),
    '34:80:b3':('Xiaomi','phone'),'38:a4:ed':('Xiaomi','phone'),
    '50:64:2b':('Xiaomi','phone'),'58:44:98':('Xiaomi','phone'),
    '60:ab:67':('Xiaomi','phone'),'64:09:80':('Xiaomi','phone'),
    '68:df:dd':('Xiaomi','phone'),'74:51:ba':('Xiaomi','phone'),
    '78:11:dc':('Xiaomi','phone'),'84:ef:18':('Xiaomi','phone'),
    '8c:be:be':('Xiaomi','phone'),'9c:99:a0':('Xiaomi','phone'),
    'a8:6b:ad':('Xiaomi','phone'),'ac:c1:ee':('Xiaomi','phone'),
    'b0:e2:35':('Xiaomi','phone'),'bc:32:b2':('Xiaomi','phone'),
    'c4:0b:cb':('Xiaomi','phone'),'d4:97:0b':('Xiaomi','phone'),
    'f0:b4:29':('Xiaomi','phone'),'f4:8b:32':('Xiaomi','phone'),
    'f8:a4:5f':('Xiaomi','phone'),'fc:64:ba':('Xiaomi','phone'),
    # OPPO / Realme / OnePlus
    '04:42:1a':('OPPO','phone'),'04:d6:aa':('OPPO','phone'),
    '18:b8:1f':('OPPO','phone'),'24:4c:07':('OPPO','phone'),
    '38:6b:bb':('OPPO','phone'),'44:65:2c':('OPPO','phone'),
    '44:9f:4e':('OPPO','phone'),'4c:91:5a':('OPPO','phone'),
    '50:a7:2b':('OPPO','phone'),'5c:77:57':('OPPO','phone'),
    '60:88:b4':('OPPO','phone'),'64:60:38':('OPPO','phone'),
    '7c:e9:d3':('OPPO','phone'),'80:4e:70':('OPPO','phone'),
    '84:7a:88':('OPPO','phone'),'90:c1:15':('OPPO','phone'),
    '94:65:2d':('OPPO','phone'),'98:0d:2e':('OPPO','phone'),
    'a0:db:64':('OPPO','phone'),'a8:57:4e':('OPPO','phone'),
    'b4:19:f1':('OPPO','phone'),'bc:c0:71':('OPPO','phone'),
    'c8:0c:c8':('OPPO','phone'),'cc:af:78':('OPPO','phone'),
    'd0:0f:21':('OPPO','phone'),'d4:6a:6a':('OPPO','phone'),
    'dc:3a:5e':('OPPO','phone'),'e0:e0:fc':('OPPO','phone'),
    'ec:d4:23':('OPPO','phone'),'f4:0e:11':('OPPO','phone'),
    'f8:59:71':('OPPO','phone'),
    # Intel (laptops/PCs)
    '00:02:b3':('Intel','laptop'),'00:03:47':('Intel','laptop'),
    '00:07:e9':('Intel','laptop'),'00:12:f0':('Intel','laptop'),
    '00:16:76':('Intel','laptop'),'00:1b:21':('Intel','laptop'),
    '00:1e:64':('Intel','laptop'),'04:0c:ce':('Intel','laptop'),
    '04:d4:c4':('Intel','laptop'),'08:11:96':('Intel','laptop'),
    '10:02:b5':('Intel','laptop'),'18:66:da':('Intel','laptop'),
    '1c:1b:0d':('Intel','laptop'),'20:16:d8':('Intel','laptop'),
    '24:77:03':('Intel','laptop'),'28:16:ad':('Intel','laptop'),
    '2c:6e:85':('Intel','laptop'),'34:02:86':('Intel','laptop'),
    '38:2c:4a':('Intel','laptop'),'40:25:c2':('Intel','laptop'),
    '44:85:00':('Intel','laptop'),'48:45:20':('Intel','laptop'),
    '4c:34:88':('Intel','laptop'),'54:27:1e':('Intel','laptop'),
    '54:e1:ad':('Intel','laptop'),'58:91:cf':('Intel','laptop'),
    '5c:51:4f':('Intel','laptop'),'60:57:18':('Intel','laptop'),
    '68:05:ca':('Intel','laptop'),'6c:88:14':('Intel','laptop'),
    '70:77:81':('Intel','laptop'),'74:86:7a':('Intel','laptop'),
    '78:2b:cb':('Intel','laptop'),'7c:b0:c2':('Intel','laptop'),
    '80:19:34':('Intel','laptop'),'84:7b:57':('Intel','laptop'),
    '88:53:2e':('Intel','laptop'),'8c:8d:28':('Intel','laptop'),
    '90:48:9a':('Intel','laptop'),'94:65:9c':('Intel','laptop'),
    '98:4f:ee':('Intel','laptop'),'9c:b6:d0':('Intel','laptop'),
    'a0:36:9f':('Intel','laptop'),'a4:02:b9':('Intel','laptop'),
    'a4:4e:31':('Intel','laptop'),'a8:7e:ea':('Intel','laptop'),
    'ac:72:89':('Intel','laptop'),'b0:6e:bf':('Intel','laptop'),
    'b8:ae:ed':('Intel','laptop'),'bc:ee:7b':('Intel','laptop'),
    'c0:18:03':('Intel','laptop'),'c4:8a:14':('Intel','laptop'),
    'c8:d9:d2':('Intel','laptop'),'d0:50:99':('Intel','laptop'),
    'd4:3d:7e':('Intel','laptop'),'d8:9e:f3':('Intel','laptop'),
    'dc:a9:71':('Intel','laptop'),'e0:94:67':('Intel','laptop'),
    'e8:6a:64':('Intel','laptop'),'ec:f4:bb':('Intel','laptop'),
    'f0:7b:cb':('Intel','laptop'),'f4:06:69':('Intel','laptop'),
    'f8:16:54':('Intel','laptop'),'fc:f8:ae':('Intel','laptop'),
    # Dell
    '00:06:5b':('Dell','laptop'),'00:14:22':('Dell','laptop'),
    '00:15:c5':('Dell','laptop'),'00:16:f0':('Dell','laptop'),
    '00:1c:23':('Dell','laptop'),'00:1d:09':('Dell','laptop'),
    '00:1e:4f':('Dell','laptop'),'00:21:70':('Dell','laptop'),
    '18:03:73':('Dell','laptop'),'1c:40:24':('Dell','laptop'),
    '24:b6:fd':('Dell','laptop'),'34:17:eb':('Dell','laptop'),
    '44:a8:42':('Dell','laptop'),'54:bf:64':('Dell','laptop'),
    '58:8a:5a':('Dell','laptop'),'5c:f9:dd':('Dell','laptop'),
    '74:86:e2':('Dell','laptop'),'80:18:44':('Dell','laptop'),
    '84:7b:eb':('Dell','laptop'),'88:51:fb':('Dell','laptop'),
    '90:b1:1c':('Dell','laptop'),'94:18:82':('Dell','laptop'),
    'a4:1f:72':('Dell','laptop'),'b0:83:fe':('Dell','laptop'),
    'b4:45:06':('Dell','laptop'),'b8:ca:3a':('Dell','laptop'),
    'c8:1f:66':('Dell','laptop'),'d4:81:d7':('Dell','laptop'),
    'e0:db:55':('Dell','laptop'),'e4:b9:7a':('Dell','laptop'),
    'e8:b0:1d':('Dell','laptop'),'f0:1f:af':('Dell','laptop'),
    'f4:8e:38':('Dell','laptop'),'f8:db:88':('Dell','laptop'),
    # Lenovo
    '04:7b:cb':('Lenovo','laptop'),'18:5e:0f':('Lenovo','laptop'),
    '1c:c1:de':('Lenovo','laptop'),'28:d2:44':('Lenovo','laptop'),
    '2c:27:d7':('Lenovo','laptop'),'3c:a9:f4':('Lenovo','laptop'),
    '40:2c:76':('Lenovo','laptop'),'48:4d:7e':('Lenovo','laptop'),
    '50:7b:9d':('Lenovo','laptop'),'54:13:79':('Lenovo','laptop'),
    '58:69:6c':('Lenovo','laptop'),'60:02:b4':('Lenovo','laptop'),
    '64:a2:f9':('Lenovo','laptop'),'70:f3:95':('Lenovo','laptop'),
    '74:c6:3b':('Lenovo','laptop'),'7c:2f:80':('Lenovo','laptop'),
    '88:70:8c':('Lenovo','laptop'),'90:7f:61':('Lenovo','laptop'),
    '9c:72:b9':('Lenovo','laptop'),'a0:1d:48':('Lenovo','laptop'),
    'ac:b5:7d':('Lenovo','laptop'),'b4:6b:fc':('Lenovo','laptop'),
    'b8:8a:ec':('Lenovo','laptop'),'c0:b8:83':('Lenovo','laptop'),
    'cc:3d:82':('Lenovo','laptop'),'dc:33:1c':('Lenovo','laptop'),
    'e0:d5:5e':('Lenovo','laptop'),'e4:a4:71':('Lenovo','laptop'),
    'e8:39:35':('Lenovo','laptop'),'f0:de:f1':('Lenovo','laptop'),
    # HP
    '00:0f:61':('HP','laptop'),'00:11:0a':('HP','laptop'),
    '00:12:79':('HP','laptop'),'00:14:38':('HP','laptop'),
    '00:15:60':('HP','laptop'),'00:17:08':('HP','laptop'),
    '00:18:71':('HP','laptop'),'00:19:bb':('HP','laptop'),
    '00:1c:c4':('HP','laptop'),'00:1e:0b':('HP','laptop'),
    '00:21:5a':('HP','laptop'),'3c:d9:2b':('HP','laptop'),
    '40:b0:34':('HP','laptop'),'5c:b9:01':('HP','laptop'),
    '60:eb:69':('HP','laptop'),'6c:3b:e5':('HP','laptop'),
    '74:46:a0':('HP','laptop'),'80:c1:6e':('HP','laptop'),
    '94:57:a5':('HP','laptop'),'98:e7:f4':('HP','laptop'),
    'a0:d3:c1':('HP','laptop'),'b4:99:ba':('HP','laptop'),
    'c8:d3:ff':('HP','laptop'),'d4:85:64':('HP','laptop'),
    'e8:b1:fc':('HP','laptop'),'f0:92:1c':('HP','laptop'),
    # ZTE (router)
    'd8:4a:2b':('ZTE','router'),'00:25:96':('ZTE','router'),
    '00:26:ed':('ZTE','router'),'08:10:74':('ZTE','router'),
    '10:75:a6':('ZTE','router'),'14:35:8b':('ZTE','router'),
    '18:68:cb':('ZTE','router'),'20:ce:20':('ZTE','router'),
    '30:b5:c2':('ZTE','router'),'34:75:c7':('ZTE','router'),
    '3c:bd:3e':('ZTE','router'),'40:c7:29':('ZTE','router'),
    '44:ca:17':('ZTE','router'),'48:22:54':('ZTE','router'),
    '4c:09:b4':('ZTE','router'),'50:3e:aa':('ZTE','router'),
    '54:22:f8':('ZTE','router'),'58:2a:f7':('ZTE','router'),
    '5c:4c:a9':('ZTE','router'),'60:20:a6':('ZTE','router'),
    '64:13:6c':('ZTE','router'),'68:89:c1':('ZTE','router'),
    '70:12:54':('ZTE','router'),'74:55:2c':('ZTE','router'),
    '78:86:92':('ZTE','router'),'7c:a1:ae':('ZTE','router'),
    '80:3a:e0':('ZTE','router'),'84:74:2a':('ZTE','router'),
    '88:6f:d4':('ZTE','router'),'8c:a6:df':('ZTE','router'),
    'a0:8c:15':('ZTE','router'),'a4:ba:db':('ZTE','router'),
    'a8:f7:e0':('ZTE','router'),'ac:b0:d8':('ZTE','router'),
    'b0:98:2b':('ZTE','router'),'b4:a2:eb':('ZTE','router'),
    'b8:08:d7':('ZTE','router'),'bc:7d:e8':('ZTE','router'),
    'c0:25:a2':('ZTE','router'),'c4:a3:66':('ZTE','router'),
    'cc:74:ad':('ZTE','router'),'d0:15:4a':('ZTE','router'),
    'd4:6b:a6':('ZTE','router'),'d8:4a:f7':('ZTE','router'),
    'dc:d2:7d':('ZTE','router'),'e0:46:9a':('ZTE','router'),
    'e4:38:3f':('ZTE','router'),'e8:08:8b':('ZTE','router'),
    'f0:a7:31':('ZTE','router'),'f4:60:e2':('ZTE','router'),
    'f8:23:b2':('ZTE','router'),'fc:7c:02':('ZTE','router'),
    # TP-Link
    '00:23:cd':('TP-Link','router'),'14:cc:20':('TP-Link','router'),
    '18:d6:c7':('TP-Link','router'),'1c:61:b4':('TP-Link','router'),
    '20:f4:1b':('TP-Link','router'),'50:c7:bf':('TP-Link','router'),
    '54:af:97':('TP-Link','router'),'64:6e:97':('TP-Link','router'),
    '70:4f:57':('TP-Link','router'),'74:ea:3a':('TP-Link','router'),
    '78:8a:20':('TP-Link','router'),'7c:b7:33':('TP-Link','router'),
    '84:16:f9':('TP-Link','router'),'88:25:93':('TP-Link','router'),
    '90:f6:52':('TP-Link','router'),'94:d9:b3':('TP-Link','router'),
    '98:da:c4':('TP-Link','router'),'a0:f3:c1':('TP-Link','router'),
    'a4:2b:b0':('TP-Link','router'),'ac:84:c9':('TP-Link','router'),
    'b0:48:7a':('TP-Link','router'),'b4:b0:24':('TP-Link','router'),
    'c0:25:06':('TP-Link','router'),'cc:32:e5':('TP-Link','router'),
    'd4:6e:0e':('TP-Link','router'),'d8:47:32':('TP-Link','router'),
    'dc:9f:db':('TP-Link','router'),'e0:05:c5':('TP-Link','router'),
    'e4:20:c0':('TP-Link','router'),'ec:26:ca':('TP-Link','router'),
    'f4:f2:6d':('TP-Link','router'),'f8:1a:67':('TP-Link','router'),
    'f8:d1:11':('TP-Link','router'),'fc:ec:da':('TP-Link','router'),
}

def _detect_vendor(mac: str) -> tuple:
    """Return (vendor, device_type) from MAC OUI. Detects randomized MACs."""
    if not mac or len(mac) < 8:
        return ('', 'unknown')
    # Check if locally administered (bit 1 of first byte = randomized MAC)
    first_byte = int(mac.split(':')[0], 16)
    if first_byte & 0x02:
        return ('موبايل', 'phone')  # randomized = almost certainly a mobile
    prefix = mac[:8].lower()
    return _OUI.get(prefix, ('', 'unknown'))

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
        vendor, device_type = _detect_vendor(mac)
        devices.append({
            "ip":          ip,
            "mac":         mac,
            "hostname":    hostname,
            "vendor":      vendor,
            "device_type": device_type,
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
