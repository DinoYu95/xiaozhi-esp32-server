import os
import base64
from typing import Optional, Dict, List, Any

import httpx

TAG = __name__


class DeviceNotFoundException(Exception):
    pass


class DeviceBindException(Exception):
    def __init__(self, bind_code):
        self.bind_code = bind_code
        super().__init__(f"设备绑定异常，绑定码: {bind_code}")


class DeviceConsentException(Exception):
    def __init__(self, prompt):
        self.prompt = prompt or ""
        super().__init__(f"设备协议未同意: {prompt}")


class ManageApiClient:
    _instance = None
    _async_clients = {}  # 为每个事件循环存储独立的客户端
    _secret = None

    def __new__(cls, config):
        """单例模式确保全局唯一实例，并支持传入配置参数"""
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._init_client(config)
        return cls._instance

    @classmethod
    def _init_client(cls, config):
        """初始化配置（延迟创建客户端）"""
        cls.config = config.get("manager-api")

        if not cls.config:
            raise Exception("manager-api配置错误")

        url = (cls.config.get("url") or "").strip()
        secret = (cls.config.get("secret") or "").strip()
        if not url or not secret:
            raise Exception("manager-api的url或secret配置错误")
        cls.config["url"] = url
        cls.config["secret"] = secret

        if "你" in secret:
            raise Exception("请先配置manager-api的secret")

        cls._secret = secret
        cls.max_retries = cls.config.get("max_retries", 6)  # 最大重试次数
        cls.retry_delay = cls.config.get("retry_delay", 10)  # 初始重试延迟(秒)
        # 不在这里创建 AsyncClient，延迟到实际使用时创建
        cls._async_clients = {}

    @classmethod
    async def _ensure_async_client(cls):
        """确保异步客户端已创建（为每个事件循环创建独立的客户端）"""
        import asyncio

        try:
            loop = asyncio.get_running_loop()
            loop_id = id(loop)

            # 为每个事件循环创建独立的客户端
            if loop_id not in cls._async_clients:
                # 服务端可能主动关闭连接，httpx 连接池无法正确检测和清理
                limits = httpx.Limits(
                    max_keepalive_connections=0,  # 禁用 keep-alive，每次都新建连接
                )
                cls._async_clients[loop_id] = httpx.AsyncClient(
                    base_url=cls.config.get("url"),
                    headers={
                        "User-Agent": f"PythonClient/2.0 (PID:{os.getpid()})",
                        "Accept": "application/json",
                        "Authorization": "Bearer " + cls._secret,
                    },
                    timeout=cls.config.get("timeout", 30),
                    limits=limits,  # 使用限制
                )
            return cls._async_clients[loop_id]
        except RuntimeError:
            # 如果没有运行中的事件循环，创建一个临时的
            raise Exception("必须在异步上下文中调用")

    @classmethod
    async def _async_request(cls, method: str, endpoint: str, **kwargs) -> Dict:
        """发送单次异步HTTP请求并处理响应"""
        # 确保客户端已创建
        client = await cls._ensure_async_client()
        endpoint = endpoint.lstrip("/")
        response = None
        try:
            response = await client.request(method, endpoint, **kwargs)
            response.raise_for_status()

            result = response.json()

            # 处理API返回的业务错误
            if result.get("code") == 10041:
                raise DeviceNotFoundException(result.get("msg"))
            elif result.get("code") == 10042:
                raise DeviceBindException(result.get("msg"))
            elif result.get("code") == 10043:
                raise DeviceConsentException(result.get("msg"))
            elif result.get("code") != 0:
                raise Exception(f"API返回错误: {result.get('msg', '未知错误')}")

            # 返回成功数据
            return result.get("data") if result.get("code") == 0 else None
        finally:
            # 确保响应被关闭（即使异常也会执行）
            if response is not None:
                await response.aclose()

    @classmethod
    def _should_retry(cls, exception: Exception) -> bool:
        """判断异常是否应该重试"""
        # 网络连接相关错误
        if isinstance(
            exception, (httpx.ConnectError, httpx.TimeoutException, httpx.NetworkError)
        ):
            return True

        # HTTP状态码错误
        if isinstance(exception, httpx.HTTPStatusError):
            status_code = exception.response.status_code
            return status_code in [408, 429, 500, 502, 503, 504]

        return False

    @classmethod
    async def _execute_async_request(cls, method: str, endpoint: str, **kwargs) -> Dict:
        """带重试机制的异步请求执行器"""
        import asyncio

        retry_count = 0

        while retry_count <= cls.max_retries:
            try:
                # 执行异步请求
                return await cls._async_request(method, endpoint, **kwargs)
            except Exception as e:
                # 判断是否应该重试
                if retry_count < cls.max_retries and cls._should_retry(e):
                    retry_count += 1
                    print(
                        f"{method} {endpoint} 异步请求失败，将在 {cls.retry_delay:.1f} 秒后进行第 {retry_count} 次重试"
                        f"（manager-api.url={cls.config.get('url')!r}）"
                    )
                    await asyncio.sleep(cls.retry_delay)
                    continue
                else:
                    # 不重试，直接抛出异常
                    raise

    @classmethod
    def safe_close(cls):
        """安全关闭所有异步连接池"""
        import asyncio

        for client in list(cls._async_clients.values()):
            try:
                asyncio.run(client.aclose())
            except Exception:
                pass
        cls._async_clients.clear()
        cls._instance = None


async def get_server_config() -> Optional[Dict]:
    """获取服务器基础配置"""
    return await ManageApiClient._instance._execute_async_request(
        "POST", "/config/server-base"
    )


async def get_agent_models(
    mac_address: str, client_id: str, selected_module: Dict
) -> Optional[Dict]:
    """获取代理模型配置"""
    return await ManageApiClient._instance._execute_async_request(
        "POST",
        "/config/agent-models",
        json={
            "macAddress": mac_address,
            "clientId": client_id,
            "selectedModule": selected_module,
        },
    )


async def generate_and_save_chat_summary(session_id: str) -> Optional[Dict]:
    """生成并保存聊天记录总结"""
    try:
        return await ManageApiClient._instance._execute_async_request(
            "POST",
            f"/agent/chat-summary/{session_id}/save",
        )
    except Exception as e:
        print(f"生成并保存聊天记录总结失败: {e}")
        return None


async def report(
    mac_address: str, session_id: str, chat_type: int, content: str, audio, report_time
) -> Optional[Dict]:
    """异步聊天记录上报"""
    if not content or not ManageApiClient._instance:
        if not content:
            print("[manage_api] 聊天上报跳过: content 为空")
        return None
    try:
        return await ManageApiClient._instance._execute_async_request(
            "POST",
            f"/agent/chat-history/report",
            json={
                "macAddress": mac_address,
                "sessionId": session_id,
                "chatType": chat_type,
                "content": content,
                "reportTime": report_time,
                "audioBase64": (
                    base64.b64encode(audio).decode("utf-8") if audio else None
                ),
            },
        )
    except Exception as e:
        print(f"TTS上报失败: {e}")
        return None


async def validate_parent_token(token: str) -> Optional[int]:
    """校验家长 token，返回 parentUserId，无效则返回 None。需 init_service 已调用。"""
    if not token or not ManageApiClient._instance:
        return None
    try:
        client = await ManageApiClient._ensure_async_client()
        r = await client.get(
            "config/parent/validate-token",
            params={"token": token},
        )
        data = r.json()
        if data.get("code") == 0:
            return data.get("data")
        return None
    except Exception as e:
        print(f"validate_parent_token 失败: {e}")
        return None


async def fetch_parent_zhiban_memory_context(
    parent_user_id: int, child_id: int
) -> Optional[Dict]:
    """
    拉取家长询问孩子时 zhiban 应用的用户命名空间与 agent/mac（与设备端主孩子一致）。
    需 init_service 已调用；失败返回 None。
    """
    if not ManageApiClient._instance:
        return None
    try:
        return await ManageApiClient._async_request(
            "GET",
            "config/parent/zhiban-memory-context",
            params={"parentUserId": parent_user_id, "childId": child_id},
        )
    except Exception as e:
        print(f"fetch_parent_zhiban_memory_context 失败: {e}")
        return None


async def save_parent_chat(
    parent_user_id: int,
    child_id: int,
    content: str,
    reply: str,
    audio_id: Optional[str] = None,
    snapshot_request_id: Optional[str] = None,
) -> Optional[Dict[str, Any]]:
    """保存家长聊天记录到 manager-api，返回 userMessageId / assistantMessageId。"""
    if not ManageApiClient._instance:
        return None
    try:
        payload = {
            "parentUserId": parent_user_id,
            "childId": child_id,
            "content": content,
            "reply": reply,
        }
        if audio_id:
            payload["audioId"] = audio_id
        if snapshot_request_id:
            payload["snapshotRequestId"] = snapshot_request_id
        data = await ManageApiClient._async_request(
            "POST",
            "config/parent/chat/save",
            json=payload,
        )
        return data if isinstance(data, dict) else None
    except Exception as e:
        print(f"save_parent_chat 失败: {e}")
        return None


async def upload_parent_chat_snapshot(
    parent_user_id: int,
    child_id: int,
    assistant_message_id: int,
    image_base64: str,
    *,
    snapshot_request_id: Optional[str] = None,
    mime_type: Optional[str] = None,
) -> Optional[Dict[str, Any]]:
    """远程看娃（旧路径）：base64 直传。Phase B 请用 prepare/finalize。"""
    if not ManageApiClient._instance:
        return None
    try:
        payload: Dict[str, Any] = {
            "parentUserId": parent_user_id,
            "childId": child_id,
            "assistantMessageId": assistant_message_id,
            "imageBase64": image_base64,
        }
        if snapshot_request_id:
            payload["snapshotRequestId"] = snapshot_request_id
        if mime_type:
            payload["mimeType"] = mime_type
        data = await ManageApiClient._async_request(
            "POST",
            "config/parent/chat/snapshot/upload",
            json=payload,
        )
        return data if isinstance(data, dict) else None
    except Exception as e:
        print(f"upload_parent_chat_snapshot 失败: {e}")
        return None


def _manager_api_public_base(config: Optional[Dict] = None) -> str:
    cfg = config or {}
    ma = cfg.get("manager-api") or {}
    if ManageApiClient._instance and not ma.get("url"):
        ma = ManageApiClient._instance.config or {}
    return (ma.get("url") or "").strip().rstrip("/")


def _device_snapshot_upload_base(config: Optional[Dict] = None) -> str:
    """设备 HTTP 回传用的 manager-api 根地址，须 ESP32 能解析（不能用 Docker 服务名 web）。"""
    cfg = config or {}
    ma = cfg.get("manager-api") or {}
    if ManageApiClient._instance and not ma.get("url"):
        ma = ManageApiClient._instance.config or {}
    for key in ("device_upload_base_url", "deviceUploadBaseUrl", "public_base_url", "publicBaseUrl"):
        val = (ma.get(key) or "").strip().rstrip("/")
        if val:
            return val
    fallback = (ma.get("url") or "").strip().rstrip("/")
    return fallback


async def prepare_parent_chat_snapshot(
    device_id: str,
    request_id: str,
    config: Optional[Dict] = None,
) -> Optional[Dict[str, Any]]:
    """Phase B：生成 uploadToken / uploadUrl / clientId。"""
    if not ManageApiClient._instance:
        return None
    try:
        upload_base = _device_snapshot_upload_base(config)
        payload = {
            "deviceId": device_id,
            "requestId": request_id,
            "taskType": "parent_snapshot",
            "uploadBaseUrl": upload_base,
        }
        data = await ManageApiClient._async_request(
            "POST",
            "config/parent/chat/snapshot/prepare",
            json=payload,
        )
        if isinstance(data, dict):
            upload_url = str(data.get("uploadUrl") or "")
            if upload_url and ("//web:" in upload_url or "://web/" in upload_url):
                print(
                    "prepare_parent_chat_snapshot 警告: uploadUrl 含 Docker 内部主机名 web，"
                    "ESP32 无法访问，请在 manager-api.url 同配置下设置 "
                    "manager-api.device_upload_base_url 为设备可达的公网/局域网地址"
                )
        return data if isinstance(data, dict) else None
    except Exception as e:
        print(f"prepare_parent_chat_snapshot 失败: {e}")
        return None


async def get_parent_chat_snapshot_status(request_id: str) -> Optional[Dict[str, Any]]:
    if not ManageApiClient._instance or not request_id:
        return None
    try:
        data = await ManageApiClient._async_request(
            "GET",
            "config/parent/chat/snapshot/status",
            params={"requestId": request_id},
        )
        return data if isinstance(data, dict) else None
    except Exception as e:
        print(f"get_parent_chat_snapshot_status 失败: {e}")
        return None


async def finalize_parent_chat_snapshot(
    parent_user_id: int,
    child_id: int,
    assistant_message_id: int,
    request_id: str,
) -> Optional[Dict[str, Any]]:
    """Phase B：设备 HTTP 上传完成后绑定聊天记录。"""
    if not ManageApiClient._instance:
        return None
    try:
        payload = {
            "parentUserId": parent_user_id,
            "childId": child_id,
            "assistantMessageId": assistant_message_id,
            "requestId": request_id,
        }
        data = await ManageApiClient._async_request(
            "POST",
            "config/parent/chat/snapshot/finalize",
            json=payload,
        )
        return data if isinstance(data, dict) else None
    except Exception as e:
        print(f"finalize_parent_chat_snapshot 失败: {e}")
        return None


async def report_device_telemetry(
    device_id: str, telemetry: Dict[str, Any]
) -> bool:
    """上报设备实时状态（电量、WiFi）到 manager-api Redis 缓存。"""
    if not device_id or not telemetry or not ManageApiClient._instance:
        return False
    if telemetry.get("batteryLevel") is None and not telemetry.get("wifiName"):
        return False
    try:
        payload: Dict[str, Any] = {"deviceId": device_id}
        if telemetry.get("batteryLevel") is not None:
            payload["batteryLevel"] = telemetry["batteryLevel"]
        if telemetry.get("wifiName"):
            payload["wifiName"] = telemetry["wifiName"]
        await ManageApiClient._instance._execute_async_request(
            "POST",
            "config/device/telemetry",
            json=payload,
        )
        return True
    except Exception as e:
        print(f"report_device_telemetry 失败: {e}")
        return False


def fetch_active_shadow_missions_sync(device_id: str, child_id: int) -> List[Dict[str, Any]]:
    """
    同步拉取当前生效的影子任务列表（按 priority、id 排序）。
    失败返回 []。
    """
    inst = ManageApiClient._instance
    if not inst or not device_id or child_id is None:
        return []
    try:
        import httpx

        url = (inst.config.get("url") or "").rstrip("/")
        secret = (inst.config.get("secret") or "").strip()
        if not url or not secret:
            return []
        with httpx.Client(
            base_url=url,
            headers={
                "Authorization": "Bearer " + secret,
                "Accept": "application/json",
            },
            timeout=float(inst.config.get("timeout", 30)),
        ) as client:
            r = client.get(
                "config/parent/shadow-mission/active",
                params={"deviceId": device_id, "childId": int(child_id)},
            )
            r.raise_for_status()
            body = r.json()
            if body.get("code") != 0:
                return []
            data = body.get("data")
            if data is None:
                return []
            if isinstance(data, list):
                return [x for x in data if isinstance(x, dict)]
            if isinstance(data, dict):
                return [data]
            return []
    except Exception as e:
        print(f"fetch_active_shadow_missions_sync 失败: {e}")
        return []


def fetch_active_shadow_mission_sync(device_id: str, child_id: int) -> Optional[Dict]:
    """兼容：仅返回列表首条，无则 None。"""
    lst = fetch_active_shadow_missions_sync(device_id, child_id)
    return lst[0] if lst else None


def init_service(config):
    ManageApiClient(config)


def manage_api_http_safe_close():
    ManageApiClient.safe_close()
