import asyncio
from aiohttp import web
from config.logger import setup_logging
from core.api.ota_handler import OTAHandler
from core.api.vision_handler import VisionHandler
from core.api.parent_chat_handler import ParentChatHandler
from core.api.parent_chat_websocket import handle_parent_chat_ws
from core.api.parent_snapshot_handler import ParentSnapshotHandler
from core.api.zhiban_tool_handler import ZhibanToolHandler

TAG = __name__


class SimpleHttpServer:
    def __init__(self, config: dict):
        self.config = config
        self.logger = setup_logging()
        self.ota_handler = OTAHandler(config)
        self.vision_handler = VisionHandler(config)
        self.parent_chat_handler = ParentChatHandler(config)
        self.zhiban_tool_handler = ZhibanToolHandler(config)
        self.parent_snapshot_handler = ParentSnapshotHandler(config)

    def _get_websocket_url(self, local_ip: str, port: int) -> str:
        """获取websocket地址

        Args:
            local_ip: 本地IP地址
            port: 端口号

        Returns:
            str: websocket地址
        """
        server_config = self.config["server"]
        websocket_config = server_config.get("websocket")

        if websocket_config and "你" not in websocket_config:
            return websocket_config
        else:
            return f"ws://{local_ip}:{port}/xiaozhi/v1/"

    async def start(self):
        try:
            server_config = self.config["server"]
            read_config_from_api = self.config.get("read_config_from_api", False)
            host = server_config.get("ip", "0.0.0.0")
            port = int(server_config.get("http_port", 8003))

            if port:
                app = web.Application()
                app["config"] = self.config

                if not read_config_from_api:
                    # 如果没有开启智控台，只是单模块运行，就需要再添加简单OTA接口，用于下发websocket接口
                    app.add_routes(
                        [
                            web.get("/xiaozhi/ota/", self.ota_handler.handle_get),
                            web.post("/xiaozhi/ota/", self.ota_handler.handle_post),
                            web.options(
                                "/xiaozhi/ota/", self.ota_handler.handle_options
                            ),
                            # 下载接口，仅提供 data/bin/*.bin 下载
                            web.get(
                                "/xiaozhi/ota/download/{filename}",
                                self.ota_handler.handle_download,
                            ),
                            web.options(
                                "/xiaozhi/ota/download/{filename}",
                                self.ota_handler.handle_options,
                            ),
                        ]
                    )
                # 添加路由
                app.add_routes(
                    [
                        web.get("/mcp/vision/explain", self.vision_handler.handle_get),
                        web.post(
                            "/mcp/vision/explain", self.vision_handler.handle_post
                        ),
                        web.options(
                            "/mcp/vision/explain", self.vision_handler.handle_options
                        ),
                        # 家长端聊天（manager-api 内部调用，需 Bearer 鉴权）
                        web.post(
                            "/internal/parent/chat",
                            self.parent_chat_handler.handle_post,
                        ),
                        web.post(
                            "/internal/parent/chat/stream",
                            self.parent_chat_handler.handle_post_stream,
                        ),
                        web.options(
                            "/internal/parent/chat",
                            self.parent_chat_handler.handle_options,
                        ),
                        web.options(
                            "/internal/parent/chat/stream",
                            self.parent_chat_handler.handle_options_stream,
                        ),
                        # 家长端聊天 WebSocket（小程序直连，与设备 8000 端口完全独立）
                        web.get("/parent/chat/ws", handle_parent_chat_ws),
                        web.post(
                            "/internal/parent/device-snapshot",
                            self.parent_snapshot_handler.handle_capture,
                        ),
                        web.options(
                            "/internal/parent/device-snapshot",
                            self.parent_snapshot_handler.handle_options,
                        ),
                        # Zhiban 工具桥接（zhiban-agent 内部调用，Bearer 鉴权）
                        web.get(
                            "/internal/zhiban/device/mcp/status",
                            self.zhiban_tool_handler.handle_device_mcp_status,
                        ),
                        web.get(
                            "/internal/zhiban/device/mcp/tools",
                            self.zhiban_tool_handler.handle_device_mcp_tools,
                        ),
                        web.post(
                            "/internal/zhiban/device/mcp/call",
                            self.zhiban_tool_handler.handle_device_mcp_call,
                        ),
                        web.get(
                            "/internal/zhiban/plugins/schemas",
                            self.zhiban_tool_handler.handle_plugin_schemas,
                        ),
                        web.post(
                            "/internal/zhiban/plugins/execute",
                            self.zhiban_tool_handler.handle_plugin_execute,
                        ),
                        web.options(
                            "/internal/zhiban/device/mcp/status",
                            self.zhiban_tool_handler.handle_options,
                        ),
                        web.options(
                            "/internal/zhiban/device/mcp/tools",
                            self.zhiban_tool_handler.handle_options,
                        ),
                        web.options(
                            "/internal/zhiban/device/mcp/call",
                            self.zhiban_tool_handler.handle_options,
                        ),
                        web.options(
                            "/internal/zhiban/plugins/schemas",
                            self.zhiban_tool_handler.handle_options,
                        ),
                        web.options(
                            "/internal/zhiban/plugins/execute",
                            self.zhiban_tool_handler.handle_options,
                        ),
                    ]
                )

                # 运行服务
                runner = web.AppRunner(app)
                await runner.setup()
                site = web.TCPSite(runner, host, port)
                await site.start()

                # 保持服务运行
                while True:
                    await asyncio.sleep(3600)  # 每隔 1 小时检查一次
        except Exception as e:
            self.logger.bind(tag=TAG).error(f"HTTP服务器启动失败: {e}")
            import traceback

            self.logger.bind(tag=TAG).error(f"错误堆栈: {traceback.format_exc()}")
            raise
