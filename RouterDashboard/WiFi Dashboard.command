#!/bin/bash
# ════════════════════════════════════════
#  WiFi Dashboard — اضغط عليه مرتين لتشغيل اللوحة
# ════════════════════════════════════════
cd "$(dirname "$0")"

# تثبيت المكتبات لو محتاج
python3 -m pip install -q -r requirements.txt 2>/dev/null

# فتح المتصفح بعد ثانيتين
(sleep 2 && open -a "Google Chrome" http://localhost:8080 2>/dev/null || open http://localhost:8080) &

# تشغيل السيرفر
python3 app.py
