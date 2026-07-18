# -*- coding: utf-8 -*-
"""设备 ID 归一化：兼容 MAC 冒号/下划线/大小写。"""
from __future__ import annotations

from typing import List


def device_id_lookup_keys(device_id: str) -> List[str]:
    raw = (device_id or "").strip()
    if not raw:
        return []
    keys: List[str] = []
    for v in (raw, raw.lower(), raw.upper()):
        if v and v not in keys:
            keys.append(v)
    if ":" in raw:
        underscored = raw.replace(":", "_")
        for v in (underscored, underscored.lower(), underscored.upper()):
            if v and v not in keys:
                keys.append(v)
    if "_" in raw and ":" not in raw:
        colonized = raw.replace("_", ":")
        for v in (colonized, colonized.lower(), colonized.upper()):
            if v and v not in keys:
                keys.append(v)
    return keys
