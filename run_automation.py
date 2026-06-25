#!/usr/bin/env python3
"""
Fetch outdoor humidity from Open-Meteo and set Govee H7151 fan speed accordingly.
Credentials are read from environment variables or a local .env file.
"""

import json
import os
import sys
import urllib.request

# ── Config (set these as env vars or fill in directly) ───────────────────────
GOVEE_EMAIL    = os.environ.get("GOVEE_EMAIL", "")
GOVEE_PASSWORD = os.environ.get("GOVEE_PASSWORD", "")
LATITUDE       = float(os.environ.get("LATITUDE", "0"))
LONGITUDE      = float(os.environ.get("LONGITUDE", "0"))

# ── Humidity → fan speed mapping ──────────────────────────────────────────────
def humidity_to_mode(humidity: int) -> tuple[int, str]:
    if humidity < 55:
        return 1, "Low"
    elif humidity < 70:
        return 2, "Medium"
    else:
        return 3, "High"

# ── HTTP helpers ──────────────────────────────────────────────────────────────
def post(url: str, payload: dict, headers: dict = {}) -> dict:
    data = json.dumps(payload).encode()
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json", **headers}, method="POST")
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())

def put(url: str, payload: dict, headers: dict = {}) -> dict:
    data = json.dumps(payload).encode()
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json", **headers}, method="PUT")
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())

def get(url: str, headers: dict = {}) -> dict:
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())

# ── Steps ─────────────────────────────────────────────────────────────────────
def login() -> str:
    print("Logging in to Govee...")
    resp = post(
        "https://app2.govee.com/account/rest/account/v1/login",
        {"email": GOVEE_EMAIL, "password": GOVEE_PASSWORD, "client": "dehumidifier-dispatch"},
    )
    token = resp.get("data", {}).get("token")
    if not token:
        sys.exit(f"Login failed: {resp.get('message', 'unknown error')}")
    print("  Logged in.")
    return token

def find_device(token: str) -> tuple[str, str]:
    print("Fetching device list...")
    resp = get(
        "https://app2.govee.com/device/rest/devices/v1/list",
        headers={"Authorization": f"Bearer {token}"},
    )
    devices = resp.get("data", {}).get("devices", [])
    for d in devices:
        if "H7151" in d.get("model", ""):
            print(f"  Found: {d['deviceName']} ({d['model']})")
            return d["device"], d["model"]
    sys.exit(f"H7151 not found. Devices: {[d['model'] for d in devices]}")

def get_humidity() -> int:
    print(f"Fetching weather at ({LATITUDE}, {LONGITUDE})...")
    url = (
        f"https://api.open-meteo.com/v1/forecast"
        f"?latitude={LATITUDE}&longitude={LONGITUDE}"
        f"&current=relative_humidity_2m&forecast_days=1"
    )
    resp = get(url)
    humidity = resp["current"]["relative_humidity_2m"]
    print(f"  Outdoor humidity: {humidity}%")
    return humidity

def set_fan_speed(token: str, device_id: str, model: str, mode: int, label: str):
    print(f"Setting fan speed to {label} (mode={mode})...")
    resp = put(
        "https://app2.govee.com/device/rest/devices/v1/control",
        {"device": device_id, "model": model, "cmd": {"name": "workMode", "value": mode}},
        headers={"Authorization": f"Bearer {token}"},
    )
    if resp.get("status") != 200:
        sys.exit(f"Control failed: {resp.get('message')}")
    print(f"  Done. Fan set to {label}.")

# ── Main ──────────────────────────────────────────────────────────────────────
if __name__ == "__main__":
    if not GOVEE_EMAIL or not GOVEE_PASSWORD:
        sys.exit("Set GOVEE_EMAIL and GOVEE_PASSWORD environment variables.")
    if LATITUDE == 0 and LONGITUDE == 0:
        sys.exit("Set LATITUDE and LONGITUDE environment variables.")

    token     = login()
    device_id, model = find_device(token)
    humidity  = get_humidity()
    mode, label = humidity_to_mode(humidity)
    set_fan_speed(token, device_id, model, mode, label)
    print(f"\nSuccess: {humidity}% humidity → fan {label}")
