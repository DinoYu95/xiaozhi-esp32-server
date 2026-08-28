package xiaozhi.modules.mindportrait.entity;

import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

@Data
@TableName("mp_parent_notification")
public class MpParentNotificationEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentUserId;
    private Long childId;
    private String nodeCode;
    private String notifyType;
    private String title;
    private String summary;
    private Integer isRead;
    private Date createTime;
}
