# -*- coding: utf-8 -*-
"""device_mcp_payload 单元测试。"""
import importlib.util
import unittest
from pathlib import Path

_spec = importlib.util.spec_from_file_location(
    "device_mcp_payload",
    Path(__file__).resolve().parent / "core" / "zhibanAgent" / "device_mcp_payload.py",
)
_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_mod)
device_mcp_call_ok = _mod.device_mcp_call_ok
flatten_device_mcp_response = _mod.flatten_device_mcp_response
image_payload_log_summary = _mod.image_payload_log_summary


class DeviceMcpPayloadTest(unittest.TestCase):
    def test_flatten_image_response(self):
        payload = {
            "action": "IMAGE",
            "result": {
                "mode": "image_only",
                "mime_type": "image/jpeg",
                "width": 640,
                "height": 480,
                "image_base64": "abc123",
                "capture_ms": 200,
                "size_bytes": 1024,
            },
            "response": None,
        }
        out = flatten_device_mcp_response(payload)
        self.assertEqual(out["image_base64"], "abc123")
        self.assertEqual(out["mime_type"], "image/jpeg")
        self.assertEqual(out["height"], 480)
        self.assertEqual(out["size_bytes"], 1024)

    def test_flatten_non_image_unchanged(self):
        payload = {
            "action": "RESPONSE",
            "response": "我看到一个杯子",
        }
        out = flatten_device_mcp_response(payload)
        self.assertNotIn("image_base64", out)

    def test_device_mcp_call_ok(self):
        self.assertTrue(device_mcp_call_ok({"action": "IMAGE"}))
        self.assertTrue(device_mcp_call_ok({"action": "RESPONSE"}))
        self.assertFalse(device_mcp_call_ok({"action": "ERROR"}))

    def test_image_payload_log_summary_no_base64(self):
        summary = image_payload_log_summary(
            {"action": "IMAGE", "image_base64": "x" * 100, "size_bytes": 50}
        )
        self.assertEqual(summary["base64_len"], 100)
        self.assertNotIn("image_base64", summary)


if __name__ == "__main__":
    unittest.main()
