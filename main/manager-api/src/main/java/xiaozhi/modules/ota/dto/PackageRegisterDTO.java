package xiaozhi.modules.ota.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Schema(description = "登记已上传 OSS 的 SWU 包（DevOps 直传 OSS 后同步 metadata）")
public class PackageRegisterDTO {

    /** 可选；与 DevOps 侧 package id 对齐 */
    private String id;

    @NotBlank
    @Pattern(regexp = "^(system|app)$")
    private String type;

    @NotBlank
    private String hardware;

    @NotBlank
    private String version;

    @NotBlank
    @Pattern(regexp = "^(stable|beta)$")
    private String channel;

    @NotBlank
    private String filename;

    @NotBlank
    private String ossKey;

    @NotNull
    private Long sizeBytes;

    @NotBlank
    private String sha256;

    private String notes;
}
