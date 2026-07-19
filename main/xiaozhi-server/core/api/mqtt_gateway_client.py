# -*- coding: utf-8 -*-
"""MQTT Gateway 管理 API 客户端（在线状态、下发 parent_snapshot）。"""
from __future__ import annotations

import hashlib
from datetime import date
from typing import Any, Dict, Optional, Set

import httpx

from config.logger import setup_logging

TAG = __name__
logger = setup_logging()


def _manager_api_base(config: dict) -> str:
    ma = (config or {}).get("manager-api") or {}
    return (ma.get("url") or "").strip().rstrip("/")


def _mqtt_manager_api(config: dict) -> str:
    server = (config or {}).get("server") or {}
    val = server.get("mqtt_manager_api") or server.get("mqttManagerApi") or ""
    val = str(val).strip()
    if not val or val.lower() == "null":
        return ""
    return val.rstrip("/")


def _mqtt_signature_key(config: dict) -> str:
    server = (config or {}).get("server") or {}
    val = server.get("mqtt_signature_key") or server.get("mqttSignatureKey") or ""
    val = str(val).strip()
    if not val or val.lower() == "null":
        return ""
    return val


def build_gateway_bearer_token(signature_key: str) -> str:
    day = date.today().strftime("%Y-%m-%d")
    return hashlib.sha256(f"{day}{signature_key}".encode("utf-8")).hexdigest()


def _gateway_headers(config: dict) -> Dict[str, str]:
    key = _mqtt_signature_key(config)
    if not key:
        return {"Content-Type": "application/json", "Accept": "application/json"}
    return {
        "Content-Type": "application/json",
        "Accept": "application/json",
        "Authorization": "Bearer " + build_gateway_bearer_token(key),
    }


def _gateway_base(config: dict) -> str:
    api = _mqtt_manager_api(config)
    if not api:
        return ""
    if api.startswith("http://") or api.startswith("https://"):
        return api.rstrip("/")
    return "http://" + api


async def query_devices_status(config: dict, client_ids: Set[str]) -> Dict[str, Dict[str, Any]]:
    """批量查询 MQTT 在线状态，返回 clientId -> statusInfo。"""
    if not client_ids:
        return {}
    base = _gateway_base(config)
    if not base:
        logger.bind(tag=TAG).warning("未配置 server.mqtt_manager_api，无法查询 MQTT 在线状态")
        return {}
    url = f"{base}/api/devices/status"
    try:
        async with httpx.AsyncClient(timeout=10.0) as client:
            resp = await client.post(
                url,
                headers=_gateway_headers(config),
                json={"clientIds": sorted(client_ids)},
            )
            resp.raise_for_status()
            body = resp.json()
    except Exception as e:
        logger.bind(tag=TAG).warning("MQTT gateway status 查询失败: %s", e)
        return {}

    data = body.get("data") if isinstance(body, dict) else body
    if isinstance(data, dict):
        return {k: v for k, v in data.items() if isinstance(v, dict)}
    return {}


def is_mqtt_client_online(status_info: Optional[Dict[str, Any]]) -> bool:
    if not status_info:
        return False
    if status_info.get("isAlive") is True:
        return True
    if status_info.get("isAlive") is False:
        return False
    if status_info.get("isAlive") is None and status_info.get("exists") is True:
        return True
    return False


async def is_device_mqtt_online(config: dict, client_id: str) -> bool:
    status_map = await query_devices_status(config, {client_id})
    return is_mqtt_client_online(status_map.get(client_id))


async def send_parent_snapshot_command(
    config: dict,
    client_id: str,
    *,
    request_id: str,
    upload_url: str,
    upload_token: str,
    max_width: int = 640,
    jpeg_quality: int = 80,
) -> bool:
    """经 mqtt-gateway 下发 parent_snapshot 命令。"""
    base = _gateway_base(config)
    if not base:
        logger.bind(tag=TAG).warning("未配置 server.mqtt_manager_api，无法下发 parent_snapshot")
        return False
    url = f"{base}/api/commands/{client_id}"
    payload = {
        "type": "parent_snapshot",
        "payload": {
            "requestId": request_id,
            "uploadUrl": upload_url,
            "uploadToken": upload_token,
            "maxWidth": max_width,
            "jpegQuality": jpeg_quality,
        },
    }
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.post(url, headers=_gateway_headers(config), json=payload)
            resp.raise_for_status()
            body = resp.json()
            if isinstance(body, dict) and body.get("success") is False:
                logger.bind(tag=TAG).warning(
                    "parent_snapshot 下发失败 clientId=%s body=%s", client_id, body
                )
                return False
            return True
    except Exception as e:
        logger.bind(tag=TAG).warning(
            "parent_snapshot 下发异常 clientId=%s requestId=%s: %s", client_id, request_id, e
        )
        return False
