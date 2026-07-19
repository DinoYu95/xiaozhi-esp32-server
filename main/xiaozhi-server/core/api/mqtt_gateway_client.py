# -*- coding: utf-8 -*-
"""MQTT Gateway 管理 API 客户端（在线状态、通用 notify 下行）。"""
from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass, field
from datetime import date
from typing import Any, Dict, Optional, Set

import httpx

from config.logger import setup_logging
from core.api.device_notify_protocol import NOTIFY_MESSAGE_TYPE

TAG = __name__
logger = setup_logging()

GATEWAY_STATUS_TIMEOUT_SEC = 10.0


@dataclass
class MqttOnlineCheckResult:
    online: bool
    reason: str
    client_id: str
    gateway_url: str = ""
    mqtt_manager_api: str = ""
    auth_configured: bool = False
    status_info: Optional[Dict[str, Any]] = None
    raw_status_map: Dict[str, Dict[str, Any]] = field(default_factory=dict)
    http_status: Optional[int] = None
    error: Optional[str] = None

    def summary(self) -> str:
        parts = [
            f"online={self.online}",
            f"reason={self.reason}",
            f"clientId={self.client_id}",
        ]
        if self.gateway_url:
            parts.append(f"url={self.gateway_url}")
        if self.status_info is not None:
            parts.append(f"status={self.status_info}")
        if self.http_status is not None:
            parts.append(f"http={self.http_status}")
        if self.error:
            parts.append(f"error={self.error}")
        return " ".join(parts)


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


def _compact_json(obj: Any, limit: int = 500) -> str:
    try:
        text = json.dumps(obj, ensure_ascii=False)
    except Exception:
        text = str(obj)
    if len(text) > limit:
        return text[:limit] + "..."
    return text


def _explain_online_from_status(status_info: Optional[Dict[str, Any]]) -> tuple[bool, str]:
    if not status_info:
        return False, "status_missing"
    is_alive = status_info.get("isAlive")
    exists = status_info.get("exists")
    bridge_alive = status_info.get("bridgeAlive")
    if is_alive is True:
        return True, "is_alive_true"
    if is_alive is False:
        if exists is True:
            return False, "is_alive_false_exists_true"
        return False, "is_alive_false"
    if is_alive is None and exists is True:
        return True, "exists_true_is_alive_null"
    if exists is False:
        return False, "gateway_no_such_connection"
    return False, "status_unknown"


async def query_devices_status(
    config: dict,
    client_ids: Set[str],
    *,
    log_context: str = "",
) -> Dict[str, Dict[str, Any]]:
    """批量查询 MQTT 在线状态，返回 clientId -> statusInfo。"""
    if not client_ids:
        return {}
    mqtt_api = _mqtt_manager_api(config)
    base = _gateway_base(config)
    ctx = f" [{log_context}]" if log_context else ""
    if not base:
        logger.bind(tag=TAG).warning(
            "MQTT 在线查询跳过{}: 未配置 server.mqtt_manager_api（当前值={!r}）",
            ctx,
            mqtt_api or "empty",
        )
        return {}
    url = f"{base}/api/devices/status"
    sorted_ids = sorted(client_ids)
    logger.bind(tag=TAG).info(
        "MQTT 在线查询开始{}: POST {} clientIds={} auth_configured={}",
        ctx,
        url,
        sorted_ids,
        bool(_mqtt_signature_key(config)),
    )
    try:
        async with httpx.AsyncClient(timeout=GATEWAY_STATUS_TIMEOUT_SEC) as client:
            resp = await client.post(
                url,
                headers=_gateway_headers(config),
                json={"clientIds": sorted_ids},
            )
            http_status = resp.status_code
            body = resp.json()
            resp.raise_for_status()
    except httpx.HTTPStatusError as e:
        body_text = ""
        try:
            body_text = (e.response.text or "")[:300]
        except Exception:
            pass
        logger.bind(tag=TAG).warning(
            "MQTT 在线查询 HTTP 失败{}: url={} status={} body={} err={}",
            ctx,
            url,
            e.response.status_code if e.response is not None else None,
            body_text,
            e,
        )
        return {}
    except Exception as e:
        logger.bind(tag=TAG).warning(
            "MQTT 在线查询异常{}: url={} timeout={}s err_type={} err={}",
            ctx,
            url,
            GATEWAY_STATUS_TIMEOUT_SEC,
            type(e).__name__,
            e,
        )
        return {}

    # gateway 直接返回 { clientId: statusInfo }；部分接口带 data 包装
    if isinstance(body, dict):
        wrapped = body.get("data")
        if isinstance(wrapped, dict):
            data = wrapped
        else:
            data = {k: v for k, v in body.items() if isinstance(v, dict)}
    else:
        data = {}
    if not data:
        logger.bind(tag=TAG).warning(
            "MQTT 在线查询响应格式异常{}: http={} body={}",
            ctx,
            http_status,
            _compact_json(body),
        )
        return {}

    status_map = data
    logger.bind(tag=TAG).info(
        "MQTT 在线查询完成{}: http={} status_map={}",
        ctx,
        http_status,
        _compact_json(status_map),
    )
    return status_map


def is_mqtt_client_online(status_info: Optional[Dict[str, Any]]) -> bool:
    online, _ = _explain_online_from_status(status_info)
    return online


async def check_device_mqtt_online(
    config: dict,
    client_id: str,
    *,
    log_context: str = "",
) -> MqttOnlineCheckResult:
    """查询单设备 MQTT 在线状态，并返回可日志化的判定详情。"""
    client_id = (client_id or "").strip()
    mqtt_api = _mqtt_manager_api(config)
    gateway_url = _gateway_base(config)
    auth_ok = bool(_mqtt_signature_key(config))
    base_result = MqttOnlineCheckResult(
        online=False,
        reason="unknown",
        client_id=client_id,
        gateway_url=gateway_url,
        mqtt_manager_api=mqtt_api,
        auth_configured=auth_ok,
    )
    if not client_id:
        base_result.reason = "empty_client_id"
        logger.bind(tag=TAG).warning("MQTT 在线判定: {}", base_result.summary())
        return base_result
    if not gateway_url:
        base_result.reason = "mqtt_manager_api_not_configured"
        logger.bind(tag=TAG).warning("MQTT 在线判定: {}", base_result.summary())
        return base_result

    status_map = await query_devices_status(
        config, {client_id}, log_context=log_context or f"clientId={client_id}"
    )
    base_result.raw_status_map = status_map
    if not status_map:
        base_result.reason = "gateway_status_unavailable"
        base_result.error = "status 接口无有效返回（连接失败、401 或响应格式错误）"
        logger.bind(tag=TAG).warning("MQTT 在线判定: {}", base_result.summary())
        return base_result

    status_info = status_map.get(client_id)
    base_result.status_info = status_info
    online, reason = _explain_online_from_status(status_info)
    base_result.online = online
    base_result.reason = reason
    log_fn = logger.bind(tag=TAG).info if online else logger.bind(tag=TAG).warning
    log_fn("MQTT 在线判定: {}", base_result.summary())
    return base_result


async def is_device_mqtt_online(config: dict, client_id: str) -> bool:
    result = await check_device_mqtt_online(config, client_id)
    return result.online


async def send_device_notify(
    config: dict,
    client_id: str,
    notify_payload: Dict[str, Any],
) -> bool:
    """经 mqtt-gateway 下发 type=notify，payload 原样推到设备。"""
    base = _gateway_base(config)
    if not base:
        logger.bind(tag=TAG).warning("未配置 server.mqtt_manager_api，无法下发 notify")
        return False
    action = (notify_payload or {}).get("action")
    request_id = (notify_payload or {}).get("requestId")
    if not action or not request_id:
        logger.bind(tag=TAG).warning("notify payload 缺少 action 或 requestId")
        return False
    url = f"{base}/api/commands/{client_id}"
    body = {
        "type": NOTIFY_MESSAGE_TYPE,
        "payload": notify_payload,
    }
    try:
        async with httpx.AsyncClient(timeout=15.0) as client:
            resp = await client.post(url, headers=_gateway_headers(config), json=body)
            if resp.status_code >= 400:
                body_text = (resp.text or "")[:500]
                logger.bind(tag=TAG).warning(
                    "notify 下发 HTTP 失败 clientId={} action={} requestId={} http={} body={}",
                    client_id,
                    action,
                    request_id,
                    resp.status_code,
                    body_text,
                )
            resp.raise_for_status()
            result = resp.json()
            if isinstance(result, dict) and result.get("success") is False:
                logger.bind(tag=TAG).warning(
                    "notify 下发失败 clientId={} action={} body={}",
                    client_id,
                    action,
                    _compact_json(result),
                )
                return False
            logger.bind(tag=TAG).info(
                "notify 下发成功 clientId={} action={} requestId={}",
                client_id,
                action,
                request_id,
            )
            return True
    except Exception as e:
        logger.bind(tag=TAG).warning(
            "notify 下发异常 clientId={} action={} requestId={} err={}",
            client_id,
            action,
            request_id,
            e,
        )
        return False
