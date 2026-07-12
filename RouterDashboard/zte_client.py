"""ZTE ZXHN H188A HTTP client — comprehensive auth for WE Egypt routers."""

import base64, hashlib, re, urllib3
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

def _b64(s: str) -> str:
    return base64.b64encode(s.encode()).decode()


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
                if self._try_login():
                    self._logged_in = True
                    return True
            except Exception:
                pass
        return False

    def _try_login(self) -> bool:
        # ── Step 1: GET login page ────────────────────────────────────────────
        try:
            r0 = self._session.get(f"{self._base}/", timeout=6)
            page    = r0.text
            action  = self._form_action(page, f"{self._base}/")
            nonce   = self._extract_nonce(page)
        except Exception:
            page, action, nonce = "", f"{self._base}/", ""

        # ── Step 2: Build password candidates ────────────────────────────────
        pw_list = [
            _sha256(self.password),                          # ZTE default
            self.password,                                   # plain
            _md5(self.password),                             # MD5
            _b64(self.password),                             # base64
            _sha256(self.username + _sha256(self.password)), # some variants
        ]
        if nonce:
            pw_list.insert(0, _sha256(self.password + nonce))
            pw_list.insert(0, _sha256(nonce + self.password))

        hdrs = {
            "Referer":      f"{self._base}/",
            "Origin":       self._base,
            "Content-Type": "application/x-www-form-urlencoded",
        }

        # ── Step 3: Try form POST ─────────────────────────────────────────────
        for pw in pw_list:
            for fields in [
                {"username": self.username, "psd":      pw},
                {"username": self.username, "password": pw},
                {"admin":    self.username, "psd":      pw},
            ]:
                try:
                    resp = self._session.post(action, data=fields,
                                             headers=hdrs, timeout=8,
                                             allow_redirects=True)
                    if self._check_success(resp):
                        return True
                except Exception:
                    continue

        # ── Step 4: JSON-RPC fallback ─────────────────────────────────────────
        return self._json_rpc_login()

    def _json_rpc_login(self) -> bool:
        for pw in (_sha256(self.password), self.password):
            for payload in [
                {"method": "login",
                 "params": {"username": self.username, "password": pw}},
                {"method": "setSystemLogin",
                 "params": [{"Username": self.username, "Password": pw}]},
            ]:
                try:
                    r = self._session.post(
                        f"{self._base}/cgi-bin/gui.cgi",
                        json=payload, timeout=5
                    )
                    if r.status_code == 200:
                        stok = self._extract_stok(r.text)
                        if stok:
                            self._stok = stok
                            return True
                        try:
                            obj = r.json()
                            if obj.get("result") in ("success", 0) or obj.get("code") == 0:
                                return True
                        except Exception:
                            pass
                except Exception:
                    continue
        return False

    def _check_success(self, resp) -> bool:
        stok = self._extract_stok(resp.url) or self._extract_stok(resp.text)
        if stok:
            self._stok = stok
            return True
        if self._session.cookies.get("sysauth"):
            return True
        # Navigated away from login page
        if (resp.status_code == 200 and
                "login" not in resp.url.lower() and
                len(resp.text) > 500 and
                any(kw in resp.text.lower() for kw in
                    ["logout", "signout", "overview", "status", "dashboard",
                     "mtu", "wan", "lan", "ssid", "wireless"])):
            return True
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
            stok_suffix = f"?stok={self._stok}" if self._stok else ""
            r = self._session.post(
                f"{self._base}/cgi-bin/gui.cgi{stok_suffix}",
                json={"method": "getSystemInfo", "params": []},
                timeout=5
            )
            obj = r.json().get("result", {})
            if not obj:
                return {}
            return {
                "internet":      obj.get("wanStatus", 1) == 1,
                "download":      round(obj.get("downRate", 0) / 1e6, 2),
                "upload":        round(obj.get("upRate",  0) / 1e6, 2),
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
                    return {
                        "internet": True, "download": 0, "upload": 0,
                        "ssid": "ZTE", "devices_count": 0, "blocked_count": 0,
                    }
        except Exception:
            pass
        return {}

    # ── Devices ───────────────────────────────────────────────────────────────

    def get_devices(self) -> list:
        if not self._session:
            return []
        try:
            stok_suffix = f"?stok={self._stok}" if self._stok else ""
            r = self._session.post(
                f"{self._base}/cgi-bin/gui.cgi{stok_suffix}",
                json={"method": "getHostInfo", "params": []},
                timeout=5
            )
            result = r.json().get("result", [])
            return [{
                "ip":       d.get("IPAddress", d.get("ip", "")),
                "mac":      d.get("MACAddress", d.get("mac", "")).lower(),
                "hostname": d.get("HostName", d.get("hostname", "Unknown")),
                "online":   d.get("Active", 1) == 1,
            } for d in result]
        except Exception:
            return []

    # ── Control ───────────────────────────────────────────────────────────────

    def set_internet(self, enabled: bool) -> bool:
        if not self._session:
            return False
        try:
            stok_suffix = f"?stok={self._stok}" if self._stok else ""
            r = self._session.post(
                f"{self._base}/cgi-bin/gui.cgi{stok_suffix}",
                json={"method": "setWanConnectStatus",
                      "params": [1 if enabled else 0]},
                timeout=5
            )
            return r.status_code == 200
        except Exception:
            return False

    def block_device(self, mac: str, block: bool) -> bool:
        if not self._session:
            return False
        try:
            stok_suffix = f"?stok={self._stok}" if self._stok else ""
            method = "addMacFilter" if block else "delMacFilter"
            r = self._session.post(
                f"{self._base}/cgi-bin/gui.cgi{stok_suffix}",
                json={"method": method, "params": [mac]},
                timeout=5
            )
            return r.status_code == 200
        except Exception:
            return False

    def set_speed_limit(self, mac: str, dl_kbps: int, ul_kbps: int) -> bool:
        if not self._session:
            return False
        try:
            stok_suffix = f"?stok={self._stok}" if self._stok else ""
            r = self._session.post(
                f"{self._base}/cgi-bin/gui.cgi{stok_suffix}",
                json={"method": "setQoSBandwidthRule",
                      "params": [{"mac": mac,
                                  "downBandwidth": dl_kbps,
                                  "upBandwidth":   ul_kbps}]},
                timeout=5
            )
            return r.status_code == 200
        except Exception:
            return False

    # ── Helpers ───────────────────────────────────────────────────────────────

    def _form_action(self, html: str, fallback: str) -> str:
        m = re.search(r'<form[^>]+action=["\']?([^"\'>\s]+)', html, re.I)
        raw = m.group(1) if m else ""
        if not raw:
            return fallback
        if raw.startswith("http"):
            return raw
        if raw.startswith("/"):
            return self._base + raw
        return self._base + "/" + raw

    def _extract_stok(self, text: str) -> str:
        m = re.search(r"stok=([a-f0-9]+)", text)
        return m.group(1) if m else ""

    def _extract_nonce(self, html: str) -> str:
        for pat in [
            r'name=["\']?rand["\']?[^>]+value=["\']?(\w+)',
            r'challenge["\']?\s*[:=]\s*["\']([a-f0-9]+)',
            r'nonce["\']?\s*[:=]\s*["\'](\w+)',
            r'var\s+rand\s*=\s*["\'](\w+)',
        ]:
            m = re.search(pat, html, re.I)
            if m:
                return m.group(1)
        return ""
