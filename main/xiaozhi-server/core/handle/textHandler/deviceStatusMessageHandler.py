from typing import Dict, Any

from core.handle.textMessageHandler import TextMessageHandler
from core.handle.textMessageType import TextMessageType
from core.utils.device_telemetry import report_device_telemetry


class DeviceStatusMessageHandler(TextMessageHandler):
    """设备状态上报：电量、WiFi 等，供小程序设备列表展示。"""

    @property
    def message_type(self) -> TextMessageType:
        return TextMessageType.DEVICE_STATUS

    async def handle(self, conn, msg_json: Dict[str, Any]) -> None:
        await report_device_telemetry(conn, msg_json, reason="device_status")
