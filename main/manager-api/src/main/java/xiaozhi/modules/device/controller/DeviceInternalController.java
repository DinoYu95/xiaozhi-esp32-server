package xiaozhi.modules.device.controller;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.device.dto.DeviceTelemetryDTO;
import xiaozhi.modules.device.service.DeviceTelemetryService;

/**
 * 设备内部接口：供 xiaozhi-server 上报实时状态，需 Bearer server.secret 鉴权（/config/**）。
 */
@RestController
@RequestMapping("/config/device")
@Tag(name = "设备内部接口")
@RequiredArgsConstructor
public class DeviceInternalController {

    private final DeviceTelemetryService deviceTelemetryService;

    @PostMapping("/telemetry")
    @Operation(summary = "上报设备实时状态（电量、WiFi）")
    public Result<Void> reportTelemetry(@RequestBody DeviceTelemetryDTO dto) {
        if (dto == null || StringUtils.isBlank(dto.getDeviceId())) {
            return new Result<Void>().error(ErrorCode.PARAMS_GET_ERROR, "deviceId 必填");
        }
        if (dto.getBatteryLevel() == null && StringUtils.isBlank(dto.getWifiName())) {
            return new Result<Void>().error(ErrorCode.PARAMS_GET_ERROR, "batteryLevel 与 wifiName 至少填一项");
        }
        deviceTelemetryService.saveTelemetry(dto);
        return new Result<Void>().ok(null);
    }
}
