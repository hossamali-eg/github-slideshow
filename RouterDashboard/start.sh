#!/bin/bash
# تشغيل لوحة التحكم بأمر واحد

set -e
cd "$(dirname "$0")"

echo "╔══════════════════════════════════════╗"
echo "║      WiFi Manager Dashboard          ║"
echo "║      لوحة تحكم الراوتر              ║"
echo "╚══════════════════════════════════════╝"
echo ""

# Check Python
if ! command -v python3 &>/dev/null; then
  echo "❌ Python3 غير موجود — حمّله من python.org"
  exit 1
fi

# Install dependencies
echo "📦 جاري تثبيت المكتبات..."
python3 -m pip install -q -r requirements.txt

echo ""
echo "✅ جاهز! سيفتح المتصفح تلقائياً..."
echo ""

# Open browser after 2 seconds
(sleep 2 && open http://localhost:8080) &

# Pass router config if provided
export ROUTER_IP="${ROUTER_IP:-192.168.1.1}"
export ROUTER_USER="${ROUTER_USER:-admin}"
export ROUTER_PASS="${ROUTER_PASS:-}"

python3 app.py
