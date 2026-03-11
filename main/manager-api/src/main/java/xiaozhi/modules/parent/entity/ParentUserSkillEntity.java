package xiaozhi.modules.parent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 家长端自定义技能表（与管理员 ai_skill 区分）
 */
@Data
@TableName("parent_user_skill")
public class ParentUserSkillEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentUserId;
    private String name;
    private String description;
    private String instructions;
    private String version;
    private String tools;
    private String metadata;

    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;
}
