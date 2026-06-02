package xiaozhi.modules.parent.vo;

import java.util.Date;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "家长端反馈列表项/提交结果")
public class ParentFeedbackVO {

    private Long id;
    private String feedbackNo;
    private String category;
    private String description;
    private Boolean blocking;
    private Boolean allowContact;
    private String status;
    @Schema(description = "运营处理备注（智控台填写，供小程序展示）")
    private String adminNote;
    @Schema(description = "暂不处理原因（status=wont_fix 时运营填写）")
    private String wontFixReason;
    private List<String> imageUrls;
    private Date createTime;
    private Date updateTime;
}
