package xiaozhi.modules.device.vo;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * Redis 中缓存的设备实时状态。
 */
@Setter
@Getter
public class DeviceStatusCacheVO implements Serializable {
    private static final long serialVersionUID = 1L;

    private Integer batteryLevel;
    private String wifiName;
    /** 上报时间戳（毫秒） */
    private Long updatedAt;
}
