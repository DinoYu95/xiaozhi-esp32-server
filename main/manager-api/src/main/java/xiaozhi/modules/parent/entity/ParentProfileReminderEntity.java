package xiaozhi.modules.learning.entity;

import java.time.LocalDate;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("parent_profile_reminder")
public class ParentProfileReminderEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long childId;
    private String reminderType;
    private String title;
    private String body;
    private String action;
    private LocalDate remindDate;
    private LocalDate promotionDate;
    private Date dismissedAt;
    private Date createTime;
}
