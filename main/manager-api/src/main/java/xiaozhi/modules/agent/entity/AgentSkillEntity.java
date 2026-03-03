package xiaozhi.modules.agent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 技能定义表（固定格式，类 Claude Skill）
 */
@Data
@TableName("ai_skill")
public class AgentSkillEntity {

    @TableId(type = IdType.INPUT)
    private String id;

    private String name;
    private String description;
    private String instructions;
    private String version;
    /** JSON 数组字符串，如 ["play_music","story_tell"] */
    private String tools;
    /** JSON 对象字符串 */
    private String metadata;

    @TableField("create_time")
    private Date createTime;
    @TableField("update_time")
    private Date updateTime;
}
