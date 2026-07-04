"""设备实时状态（电量、WiFi）解析与上报 manager-api。"""

import asyncio
import json
from typing import Any, Dict, Optional

from core.utils.util import sanitize_tool_name

TAG = __name__

# MCP 工具 self.get_device_status 在 xiaozhi 内部键名为 sanitize 后的形式
MCP_DEVICE_STATUS_TOOL = sanitize_tool_name("self.get_device_status")

# 固件 self.get_device_status 标准返回（节选，供小程序只取 battery / network.ssid）:
# {
#   "audio_speaker": {"volume": 70},
#   "screen": {"brightness": 75, "theme": "light"},
#   "battery": {"level": 85, "charging": false},
#   "network": {"type": "wifi", "ssid": "...", "signal": "strong"},
#   "chip": {"temperature": 42.5}
# }


def extract_telemetry(payload: Dict[str, Any]) -> Optional[Dict[str, Any]]:
    """从 MCP get_device_status、hello、device_status 消息提取小程序所需字段。"""
    if not isinstance(payload, dict) or not payload:
        return None

    battery: Optional[int] = None
    wifi: Optional[str] = None

    def _pick_battery(val: Any) -> Optional[int]:
        if isinstance(val, (int, float)):
            return int(val)
        if isinstance(val, dict) and val.get("level") is not None:
            return int(val["level"])
        return None

    def _pick_wifi(val: Any) -> Optional[str]:
        if isinstance(val, str) and val.strip():
            return val.strip()
        return None

    # 1. 优先按 self.get_device_status 标准嵌套结构解析
    picked = _pick_battery(payload.get("battery"))
    if picked is not None:
        battery = picked

    network = payload.get("network")
    if isinstance(network, dict):
        wifi = _pick_wifi(network.get("ssid"))

    # 2. hello / device_status 扁平字段（固件可选 shorthand）
    if battery is None:
        for key in ("battery_level", "batteryLevel"):
            picked = _pick_battery(payload.get(key))
            if picked is not None:
                battery = picked
                break

    if wifi is None:
        for key in ("wifi_ssid", "wifiName", "ssid", "wifi_name"):
            picked = _pick_wifi(payload.get(key))
            if picked:
                wifi = picked
                break

    board = payload.get("board")
    if wifi is None and isinstance(board, dict):
        wifi = _pick_wifi(board.get("ssid"))

    for nested_key in ("device_reported_context", "environment"):
        nested = payload.get(nested_key)
        if isinstance(nested, dict):
            sub = extract_telemetry(nested)
            if sub:
                if battery is None and sub.get("batteryLevel") is not None:
                    battery = sub["batteryLevel"]
                if wifi is None and sub.get("wifiName"):
                    wifi = sub["wifiName"]

    if battery is None and wifi is None:
        return None

    result: Dict[str, Any] = {}
    if battery is not None:
        result["batteryLevel"] = max(0, min(100, int(battery)))
    if wifi:
        result["wifiName"] = wifi
    return result


def parse_mcp_status_text(text: str) -> Optional[Dict[str, Any]]:
    if not text or not isinstance(text, str):
        return None
    try:
        data = json.loads(text)
    except json.JSONDecodeError:
        return None
    if isinstance(data, dict):
        return extract_telemetry(data)
    return None


async def report_device_telemetry(conn, source: Dict[str, Any], reason: str = "") -> None:
    """解析 source 并异步上报到 manager-api。"""
    device_id = getattr(conn, "device_id", None)
    if not device_id:
        return
    telemetry = extract_telemetry(source)
    if not telemetry:
        return
    asyncio.create_task(_do_report(conn, device_id, telemetry, reason))


async def report_telemetry_dict(conn, telemetry: Dict[str, Any], reason: str = "") -> None:
    device_id = getattr(conn, "device_id", None)
    if not device_id or not telemetry:
        return
    asyncio.create_task(_do_report(conn, device_id, telemetry, reason))


async def _do_report(conn, device_id: str, telemetry: Dict[str, Any], reason: str) -> None:
    try:
        from config.manage_api_client import report_device_telemetry

        ok = await report_device_telemetry(device_id, telemetry)
        if ok:
            conn.logger.bind(tag=TAG).info(
                "设备遥测上报成功 device=%s reason=%s data=%s",
                device_id,
                reason,
                telemetry,
            )
    except Exception as e:
        conn.logger.bind(tag=TAG).warning("设备遥测上报失败: %s", e)
