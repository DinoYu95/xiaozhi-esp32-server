package xiaozhi.modules.ota.vo;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "设备端 manifest")
public class DeviceOtaCheckRespVO {

    private Map<String, UpdateItem> updates = new LinkedHashMap<>();

    @Data
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public static class UpdateItem {
        private String version;
        private String url;
        private String sha256;
        private Long releaseId;
        private Boolean mandatory;
    }
}
