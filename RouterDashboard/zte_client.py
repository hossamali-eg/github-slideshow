"""ZTE ZXHN H188A HTTP client — best-effort, falls back gracefully."""

import hashlib, re, urllib3
urllib3.disable_warnings()

try:
    import requests
    _HAS_REQUESTS = True
except ImportError:
    _HAS_REQUESTS = False


def _sha256(s: str) -> str:
    return hashlib.sha256(s.encode()).hexdigest()

def _md5(s: str) -> str:
    return hashlib.md5(s.encode()).hexdigest()


class ZTEClient:
    def __init__(self, ip: str, username: str, password: str):
        self.configure(ip, username, password)

    def configure(self, ip: str, username: str, password: str):
        self.ip       = ip
        self.username = username
        self.password = password
        self._base    = f"http://{ip}"
        self._stok    = ""
        self._session = requests.Session() if _HAS_REQUESTS else None
        if self._session:
            self._session.verify = False
            self._session.headers.update({"User-Agent": "Mozilla/5.0"})

    # ── Login ─────────────────────────────────────────────────────────────────

    def login(self) -> bool:
        if not self._session:
            return False
        for proto in ("http", "https"):
            self._base = f"{proto}://{self.ip}"
            if self._try_login():
                return True
        return False

    def _try_login(self) -> bool:
        try:
            r = self._session.get(f"{self._base}/", timeout=5)
            page = r.text
            action = self._form_action(page, r.url)

            for fields in [
                {"username": self.username, "psd": _sha256(self.password)},
                {"username": self.username, "psd": self.password},
                {"username": self.username, "psd": _md5(self.password)},
                {"username": self.username, "password": self.password},
                {"luci_username": self.username, "luci_password": self.password},
            ]:
                resp = self._session.post(action, data=fields,
                                          headers={"Referer": self._base},
                                          timeout=8, allow_redirects=True)
                stok = self._extract_stok(resp.url) or self._extract_stok(resp.text)
                if stok:
                    self._stok = stok
                    return True
                cookies = self._session.cookies.get("sysauth")
                if cookies:
                    return True
        except Exception:
            pass
        return False

    # ── Status ────────────────────────────────────────────────────────────────

    def get_status(self) -> dict:
        if not self._session:
            return {}
        for fn in (self._status_via_json, self._status_via_page):
            data = fn()
            if data:
                return data
        return {}

    def _status_via_json(self) -> dict:
        try:
            import json
            payload = {"method": "getSystemInfo", "params": []}
            r = self._session.post(f"{self._base}/cgi-bin/gui.cgi",
                                   json=payload, timeout=5)
            obj = r.json().get("result", {})
            if not obj:
                return {}
            return {
                "internet":      obj.get("wanStatus", 1) == 1,
                "download":      round(obj.get("downRate", 0) / 1e6, 2),
                "upload":        round(obj.get("upRate", 0) / 1e6, 2),
                "ssid":          obj.get("ssid", "ZTE"),
                "devices_count": obj.get("hostCount", 0),
                "blocked_count": 0,
            }
        except Exception:
            return {}

    def _status_via_page(self) -> dict:
        try:
            urls = [
                f"{self._base}/getpage.gch?pid=1002003",
                f"{self._base}/status.asp",
                f"{self._base}/index.asp",
            ]
            for url in urls:
                r = self._session.get(url, timeout=5)
                if r.status_code == 200 and len(r.text) > 200:
                    return {"internet": True, "download": 0,
                            "upload": 0, "ssid": "ZTE", "devices_count": 0}
        except Exception:
            pass
        return {}

    # ── Devices ───────────────────────────────────────────────────────────────

    def get_devices(self) -> list:
        if not self._session:
            return []
        try:
            payload = {"method": "getHostInfo", "params": []}
            r = self._session.post(f"{self._base}/cgi-bin/gui.cgi",
                                   json=payload, timeout=5)
            result = r.json().get("result", [])
            devices = []
            for d in result:
                devices.append({
                    "ip":       d.get("IPAddress", d.get("ip", "")),
                    "mac":      d.get("MACAddress", d.get("mac", "")).lower(),
                    "hostname": d.get("HostName", d.get("hostname", "Unknown")),
                    "online":   d.get("Active", 1) == 1,
                })
            return devices
        except Exception:
            return []

    # ── Control ───────────────────────────────────────────────────────────────

    def set_internet(self, enabled: bool) -> bool:
        if not self._session:
            return False
        try:
            payload = {"method": "setWanConnectStatus",
                       "params": [1 if enabled else 0]}
            r = self._session.post(f"{self._base}/cgi-bin/gui.cgi",
                                   json=payload, timeout=5)
            return r.status_code == 200
        except Exception:
            return False

    def block_device(self, mac: str, block: bool) -> bool:
        if not self._session:
            return False
        try:
            method = "addMacFilter" if block else "delMacFilter"
            payload = {"method": method, "params": [mac]}
            r = self._session.post(f"{self._base}/cgi-bin/gui.cgi",
                                   json=payload, timeout=5)
            return r.status_code == 200
        except Exception:
            return False

    # ── Helpers ───────────────────────────────────────────────────────────────

    def _form_action(self, html: str, fallback: str) -> str:
        m = re.search(r'<form[^>]+action=["\']?([^"\'>\s]+)', html, re.I)
        raw = m.group(1) if m else ""
        if raw.startswith("http"):
            return raw
        if raw.startswith("/"):
            return self._base + raw
        return fallback

    def _extract_stok(self, text: str) -> str:
        m = re.search(r"stok=([a-f0-9]+)", text)
        return m.group(1) if m else ""
