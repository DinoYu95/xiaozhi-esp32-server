package xiaozhi.modules.parent.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 家长为该设备设置的规则（如「不要讲鬼故事」「少提零食」）
 */
@Data
@TableName("parent_device_rule")
public class ParentDeviceRuleEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 设备 ID（与 ai_device.id 一致） */
    private String deviceId;
    /** 家长用户 ID */
    private Long parentUserId;
    /** 规则内容 */
    private String ruleText;
    /** 创建时间 */
    private Date createTime;
}
