package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长头像上传返回")
public class ParentAvatarUploadVO {
    @Schema(description = "头像完整访问 URL（可写入 PUT /profile 的 avatarUrl）")
    private String avatarUrl;
}
