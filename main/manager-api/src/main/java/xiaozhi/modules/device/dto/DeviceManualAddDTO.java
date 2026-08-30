package xiaozhi.modules.device.dto;

import lombok.Data;

@Data
public class DeviceManualAddDTO {
    private String agentId;
    private String board;        // 设备型号
    private String appVersion;   // 固件 / 系统版本（写入 system_version）
    private String deviceType;   // 业务设备类型
    private String otaChannel;   // stable|beta
    private String macAddress;   // Mac地址
} 