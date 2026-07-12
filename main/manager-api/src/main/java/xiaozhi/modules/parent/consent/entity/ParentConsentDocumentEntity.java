package xiaozhi.modules.parent.consent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("parent_consent_document")
public class ParentConsentDocumentEntity {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ARCHIVED = "archived";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String version;
    private String title;
    private String summary;
    private String content;
    private String status;
    private Date publishedAt;
    private Date createTime;
    private Date updateTime;
}
