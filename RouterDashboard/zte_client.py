"""
ZTE ZXHN H188A HTTP client.

Implements the real ZTE "Lua CGI" web-management protocol used by most
consumer ZTE gateways (H188A/H288A/F660/F680/...), reverse-engineered by
the open-source project https://github.com/juacas/zte_tracker.

Login sequence:
  1. GET  /?_type=loginData&_tag=login_token&_=<ms>   -> numeric token
  2. GET  /?_type=loginData&_tag=login_entry          -> sess_token + cookie
  3. POST /?_type=loginData&_tag=login_entry
         action=login&Username=..&Password=sha256(pwd+token)&_sessionTOKEN=..

Devices are read from menuData endpoints returning XML <Instance> blocks
(LAN: accessdev_landevs_lua.lua, WLAN: wlan_client_stat_lua.lua).

The exact Lua script names differ slightly across ZTE firmware builds, so
several known variants are tried in turn. Internet on/off and per-device
block/speed-limit are NOT part of the documented protocol above (that
project only reads data) — those remain best-effort guesses that may not
work on every firmware; ARP-based device listing + manual rename always
work regardless.
"""

import hashlib, re, time, urllib3
import xml.etree.ElementTree as ET

urllib3.disable_warnings()

try:
    import requests
    _HAS_REQUESTS = True
except ImportError:
    _HAS_REQUESTS = False


def _sha256(s: str) -> str:
    return hashlib.sha256(s.encode()).hexdigest()


# Lua script name variants seen across ZTE firmware builds
_LAN_SCRIPTS  = ["accessdev_landevs_lua.lua", "lan_client_stat_lua.lua"]
_WLAN_SCRIPTS = ["wlan_client_stat_lua.lua", "accessdev_ssiddev_lua.lua"]


class ZTEClient:
    def __init__(self, ip: str, username: str, password: str):
        self._logged_in = False
        self.configure(ip, username, password)

    def configure(self, ip: str, username: str, password: str):
        self.ip       = ip
        self.username = username
        self.password = password
        self._base    = f"http://{ip}"
        self._stok    = ""
        self._logged_in = False
        self._session = requests.Session() if _HAS_REQUESTS else None
        if self._session:
            self._session.verify = False
            self._session.headers.update({
                "User-Agent": (
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/120.0.0.0 Safari/537.36"
                )
            })

    # ── Login ─────────────────────────────────────────────────────────────────

    def login(self) -> bool:
        if not self._session or not self.password:
            return False
        for proto in ("http", "https"):
            self._base = f"{proto}://{self.ip}"
            try:
                if self._lua_login():
                    self._logged_in = True
                    return True
            except Exception:
                pass
        return False

    def _lua_login(self) -> bool:
        # Step 1: numeric login token
        r1 = self._session.get(
            f"{self._base}/", params={"_type": "loginData", "_tag": "login_token",
                                       "_": str(int(time.time() * 1000))},
            timeout=6
        )
        token_m = re.search(r"(\d{5,})", r1.text)
        if not token_m:
            return False
        login_token = token_m.group(1)

        # Step 2: session token + cookie
        r2 = self._session.get(
            f"{self._base}/", params={"_type": "loginData", "_tag": "login_entry"},
            timeout=6
        )
        sess_m = re.search(r'"?sess_token"?\s*[:=]\s*"?([a-zA-Z0-9]+)"?', r2.text)
        sess_token = sess_m.group(1) if sess_m else ""

        # Step 3: submit credentials
        pw_hash = _sha256(self.password + login_token)
        r3 = self._session.post(
            f"{self._base}/",
            params={"_type": "loginData", "_tag": "login_entry"},
            data={
                "action":         "login",
                "Username":       self.username,
                "Password":       pw_hash,
                "_sessionTOKEN":  sess_token,
            },
            headers={"Referer": f"{self._base}/"},
            timeout=8,
        )
        ok = ('"login_status":"1"' in r3.text or '"login_status":1' in r3.text or
              "login_state=1" in r3.text or r3.status_code == 200 and
              "error" not in r3.text.lower() and len(r3.text) > 0)
        # Verify by trying to fetch a protected page
        if ok:
            verify = self._session.get(
                f"{self._base}/",
                params={"_type": "menuView", "_tag": "localNetStatus"},
                timeout=6,
            )
            if "login" not in verify.text.lower()[:200]:
                return True
        return False

    # ── Status ────────────────────────────────────────────────────────────────

    def get_status(self) -> dict:
        if not self._session:
            return {}
        devices = self.get_devices()
        return {
            "internet":      True,
            "download":      0,
            "upload":        0,
            "ssid":          "ZTE",
            "devices_count": len(devices),
            "blocked_count": 0,
        }

    # ── Devices ───────────────────────────────────────────────────────────────

    def get_devices(self) -> list:
        if not self._session:
            return []
        devices = {}
        for scripts in (_LAN_SCRIPTS, _WLAN_SCRIPTS):
            for script in scripts:
                for d in self._fetch_device_script(script):
                    if d["mac"]:
                        devices[d["mac"]] = d
                if devices:
                    break
        return list(devices.values())

    def _fetch_device_script(self, script: str) -> list:
        try:
            # Some firmwares require a menuView "page load" call first
            self._session.get(
                f"{self._base}/",
                params={"_type": "menuView", "_tag": "localNetStatus"},
                timeout=5,
            )
            r = self._session.get(
                f"{self._base}/",
                params={"_type": "menuData", "_tag": script},
                timeout=6,
            )
            return self._parse_instances(r.text)
        except Exception:
            return []

    def _parse_instances(self, xml_text: str) -> list:
        devices = []
        try:
            root = ET.fromstring(xml_text)
        except ET.ParseError:
            return self._parse_instances_regex(xml_text)

        for instance in root.iter("Instance"):
            params = {}
            for child in instance:
                name = child.get("name") or child.tag
                params[name] = (child.text or "").strip()
            mac = params.get("MACAddress", params.get("mac", ""))
            if not mac:
                continue
            devices.append({
                "ip":       params.get("IPAddress", params.get("ip", "")),
                "mac":      mac.lower(),
                "hostname": params.get("HostName", params.get("hostname", "")) or mac,
                "online":   params.get("Active", "1") in ("1", "true", "True", "yes"),
            })
        return devices

    def _parse_instances_regex(self, text: str) -> list:
        devices = []
        for block in re.findall(r"<Instance>(.*?)</Instance>", text, re.S):
            mac_m  = re.search(r"MACAddress[^>]*>([0-9a-fA-F:]{17})", block)
            ip_m   = re.search(r"IPAddress[^>]*>([\d.]+)", block)
            name_m = re.search(r"HostName[^>]*>([^<]*)", block)
            if not mac_m:
                continue
            devices.append({
                "ip":       ip_m.group(1) if ip_m else "",
                "mac":      mac_m.group(1).lower(),
                "hostname": (name_m.group(1).strip() if name_m and name_m.group(1).strip()
                             else mac_m.group(1)),
                "online":   True,
            })
        return devices

    # ── Control (best-effort; not confirmed against real firmware) ─────────────

    def set_internet(self, enabled: bool) -> bool:
        if not self._session:
            return False
        try:
            r = self._session.post(
                f"{self._base}/",
                params={"_type": "setData", "_tag": "wan_conn_status"},
                data={"Enable": "1" if enabled else "0"},
                timeout=5,
            )
            return r.status_code == 200
        except Exception:
            return False

    def block_device(self, mac: str, block: bool) -> bool:
        if not self._session:
            return False
        try:
            r = self._session.post(
                f"{self._base}/",
                params={"_type": "setData", "_tag": "wlan_mac_filter"},
                data={"MACAddress": mac, "Enable": "1" if block else "0"},
                timeout=5,
            )
            return r.status_code == 200
        except Exception:
            return False

    def set_speed_limit(self, mac: str, dl_kbps: int, ul_kbps: int) -> bool:
        if not self._session:
            return False
        try:
            r = self._session.post(
                f"{self._base}/",
                params={"_type": "setData", "_tag": "qos_bandwidth_rule"},
                data={"MACAddress": mac, "DownBandwidth": dl_kbps, "UpBandwidth": ul_kbps},
                timeout=5,
            )
            return r.status_code == 200
        except Exception:
            return False
