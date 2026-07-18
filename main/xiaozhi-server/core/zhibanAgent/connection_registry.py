# -*- coding: utf-8 -*-
"""Zhiban 场景：活跃设备 WebSocket 连接索引，供 internal tool API 查找 ConnectionHandler。"""
from __future__ import annotations

import threading
from typing import Any, Optional

from core.zhibanAgent.device_id_normalize import device_id_lookup_keys

_lock = threading.RLock()
_by_device: dict[str, Any] = {}
_by_session: dict[str, Any] = {}


def register(conn) -> None:
    """连接完成组件初始化且为 ZhibanAgent 时注册。"""
    device_id = getattr(conn, "device_id", None) or ""
    session_id = getattr(conn, "session_id", None) or ""
    with _lock:
        for key in device_id_lookup_keys(device_id):
            _by_device[key] = conn
        if session_id:
            _by_session[session_id] = conn


def unregister(conn) -> None:
    """连接关闭时注销。"""
    device_id = getattr(conn, "device_id", None) or ""
    session_id = getattr(conn, "session_id", None) or ""
    with _lock:
        for key in device_id_lookup_keys(device_id):
            if _by_device.get(key) is conn:
                _by_device.pop(key, None)
        if session_id and _by_session.get(session_id) is conn:
            _by_session.pop(session_id, None)


def resolve(
    device_id: Optional[str] = None, session_id: Optional[str] = None
) -> Optional[Any]:
    with _lock:
        if session_id:
            conn = _by_session.get(session_id)
            if conn is not None:
                return conn
        if device_id:
            for key in device_id_lookup_keys(device_id):
                conn = _by_device.get(key)
                if conn is not None:
                    return conn
    return None
