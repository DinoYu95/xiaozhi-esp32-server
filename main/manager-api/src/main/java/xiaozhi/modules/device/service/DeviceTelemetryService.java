package xiaozhi.modules.device.service;

import xiaozhi.modules.device.dto.DeviceTelemetryDTO;
import xiaozhi.modules.device.vo.DeviceStatusCacheVO;

public interface DeviceTelemetryService {

    /** 缓存 TTL：与在线判断窗口一致（5 分钟） */
    long STATUS_TTL_SECONDS = 300L;

    void saveTelemetry(DeviceTelemetryDTO dto);

    DeviceStatusCacheVO getStatus(String deviceId);
}
