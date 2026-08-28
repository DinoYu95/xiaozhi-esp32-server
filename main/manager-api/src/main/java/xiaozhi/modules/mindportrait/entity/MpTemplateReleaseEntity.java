package xiaozhi.modules.mindportrait.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("mp_template_release")
public class MpTemplateReleaseEntity {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ARCHIVED = "archived";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String ageBand;
    private String versionLabel;
    private Long teachingSubmissionId;
    private String rulesJson;
    private String status;
    private Date publishedAt;
    private Date createTime;
}
