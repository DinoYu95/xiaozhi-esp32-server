package xiaozhi.modules.device.service;

import xiaozhi.modules.device.dto.DeviceTelemetryDTO;
import xiaozhi.modules.device.vo.DeviceStatusCacheVO;

public interface DeviceTelemetryService {

    void saveTelemetry(DeviceTelemetryDTO dto);

    DeviceStatusCacheVO getStatus(String deviceId);
}
