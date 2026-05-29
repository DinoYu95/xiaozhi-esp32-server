package xiaozhi.modules.parent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("parent_feedback")
public class ParentFeedbackEntity {

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_PROCESSING = "processing";
    public static final String STATUS_RESOLVED = "resolved";
    public static final String STATUS_WONT_FIX = "wont_fix";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String feedbackNo;
    private Long parentUserId;
    private String category;
    private String description;
    private Integer blocking;
    private Integer allowContact;
    private String status;
    private String contextSnapshot;
    private String imageUrls;
    private String adminNote;
    private String wontFixReason;
    private Date createTime;
    private Date updateTime;
}
