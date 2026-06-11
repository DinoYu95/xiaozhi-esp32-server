package xiaozhi.modules.parent.storage.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端文件上传结果")
public class ParentStorageUploadVO {

    @Schema(description = "存储类别：avatar / feedback")
    private String category;

    @Schema(description = "对象键（推荐写入业务接口：profile.avatarUrl 或 feedback.imageUrls）")
    private String objectKey;

    @Schema(description = "可直接用于 image 组件展示的 HTTPS URL")
    private String accessUrl;

    @Schema(description = "是否已上传至 OSS（false 表示仍落本地 uploadfile）")
    private Boolean oss;
}
