package xiaozhi.modules.parent.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "反馈截图上传结果")
public class ParentFeedbackImageUploadVO {

    @Schema(description = "完整可访问 URL，提交反馈时放入 imageUrls")
    private String imageUrl;
}
