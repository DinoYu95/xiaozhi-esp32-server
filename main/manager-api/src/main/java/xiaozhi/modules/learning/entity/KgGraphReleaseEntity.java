package xiaozhi.modules.learning.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("kg_graph_release")
public class KgGraphReleaseEntity {

    public static final String STATUS_DRAFT = "draft";
    public static final String STATUS_PUBLISHED = "published";
    public static final String STATUS_ARCHIVED = "archived";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String versionLabel;
    private String status;
    private String subject;
    private Integer gradeMin;
    private Integer gradeMax;
    private Date publishedAt;
    private String checksum;
    /** 省编码，CN=全国通用 */
    private String provinceCode;
    private String textbookEdition;
    private Date createTime;
    private Date updateTime;
}
