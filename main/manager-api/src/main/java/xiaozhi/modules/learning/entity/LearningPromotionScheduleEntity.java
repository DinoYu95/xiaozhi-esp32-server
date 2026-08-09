package xiaozhi.modules.learning.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("learning_promotion_schedule")
public class LearningPromotionScheduleEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 空串表示全局默认 */
    private String provinceCode;
    /** PRIMARY | MIDDLE | HIGH */
    private String schoolLevel;
    private Integer promotionMonth;
    private Integer promotionDay;
    private String remark;
    private Date updateTime;
}
