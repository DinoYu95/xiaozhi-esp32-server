package xiaozhi.modules.ota.util;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import xiaozhi.modules.device.dto.DeviceReportReqDTO;
import xiaozhi.modules.device.entity.DeviceEntity;

/**
 * 绑定缓存 / 上报字段：system_version = 固件，app_version = 应用 SWU。
 */
public final class OtaDeviceFieldBinder {

    public static final String CACHE_BOARD = "board";
    public static final String CACHE_SYSTEM_VERSION = "system_version";
    public static final String CACHE_APP_VERSION = "app_version";
    public static final String CACHE_DEVICE_TYPE = "device_type";
    public static final String CACHE_OTA_CHANNEL = "ota_channel";

    private OtaDeviceFieldBinder() {
    }

    public static void putReportIntoCache(Map<String, Object> cache, DeviceReportReqDTO report) {
        if (cache == null || report == null) {
            return;
        }
        String firmware = firstNonBlank(report.getSystemVersion(),
                report.getApplication() != null ? report.getApplication().getVersion() : null);
        cache.put(CACHE_SYSTEM_VERSION, firmware);
        cache.put(CACHE_APP_VERSION, StringUtils.trimToNull(report.getAppVersion()));
        cache.put(CACHE_DEVICE_TYPE, StringUtils.trimToNull(report.getDeviceType()));
        cache.put(CACHE_OTA_CHANNEL, StringUtils.defaultIfBlank(report.getOtaChannel(), "stable"));
        if (report.getBoard() != null && StringUtils.isNotBlank(report.getBoard().getType())) {
            cache.put(CACHE_BOARD, report.getBoard().getType());
        }
    }

    public static void applyCacheToDevice(DeviceEntity device, Map<String, Object> cache) {
        if (device == null || cache == null) {
            return;
        }
        String system = str(cache.get(CACHE_SYSTEM_VERSION));
        String app = str(cache.get(CACHE_APP_VERSION));
        if (StringUtils.isBlank(system) && StringUtils.isNotBlank(app)) {
            system = app;
            app = null;
        }
        if (StringUtils.isNotBlank(system)) {
            device.setSystemVersion(system);
        }
        device.setAppVersion(app);
        String deviceType = str(cache.get(CACHE_DEVICE_TYPE));
        if (StringUtils.isNotBlank(deviceType)) {
            device.setDeviceType(deviceType);
        }
        device.setOtaChannel(StringUtils.defaultIfBlank(str(cache.get(CACHE_OTA_CHANNEL)), "stable"));
        String board = str(cache.get(CACHE_BOARD));
        if (StringUtils.isNotBlank(board) && StringUtils.isBlank(device.getBoard())) {
            device.setBoard(board);
        }
    }

    private static String firstNonBlank(String a, String b) {
        return StringUtils.isNotBlank(a) ? a.trim() : StringUtils.trimToNull(b);
    }

    private static String str(Object v) {
        return v == null ? null : StringUtils.trimToNull(String.valueOf(v));
    }
}
