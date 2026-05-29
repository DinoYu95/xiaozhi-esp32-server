package xiaozhi.modules.parent.vo;

import java.util.Date;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "管理端反馈详情")
public class ParentFeedbackAdminVO {

    private Long id;
    private String feedbackNo;
    private Long parentUserId;
    private String parentNickname;
    private String category;
    private String description;
    private Boolean blocking;
    private Boolean allowContact;
    private String status;
    private Map<String, Object> contextSnapshot;
    private List<String> imageUrls;
    private String adminNote;
    private String wontFixReason;
    private Date createTime;
    private Date updateTime;
}
