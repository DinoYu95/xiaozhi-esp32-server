package xiaozhi.modules.parent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 家长影子任务：限时引导孩子行为/话术，孩子与智伴对话时注入；家长长期规则优先。
 */
@Data
@TableName("parent_shadow_mission")
public class ParentShadowMissionEntity {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_CANCELLED = "cancelled";
    public static final String STATUS_EXPIRED = "expired";
    public static final String STATUS_COMPLETED = "completed";

    @TableId(type = IdType.AUTO)
    private Long id;
    private String deviceId;
    private Long childId;
    private Long parentUserId;
    private String title;
    private String instructions;
    private Date endsAt;
    private String status;
    /** 越小越优先 */
    private Integer priority;
    private Date createTime;
    private Date updateTime;
}
