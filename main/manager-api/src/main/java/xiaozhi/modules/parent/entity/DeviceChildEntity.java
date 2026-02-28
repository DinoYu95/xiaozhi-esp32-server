package xiaozhi.modules.parent.entity;

import java.time.LocalDate;
import java.util.Date;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 设备主孩子表（一设备一孩）
 */
@Data
@TableName("device_child")
@Schema(description = "设备主孩子")
public class DeviceChildEntity {

    @TableId(type = IdType.AUTO)
    @Schema(description = "主键")
    private Long id;

    @Schema(description = "设备ID（= ai_device.id）")
    private String deviceId;

    @Schema(description = "孩子姓名/昵称")
    private String name;

    @Schema(description = "头像URL")
    private String avatarUrl;

    @Schema(description = "生日")
    private LocalDate birthday;

    @Schema(description = "性别：0未知/1男/2女")
    private Integer gender;

    @Schema(description = "年龄段")
    private String ageStage;

    @Schema(description = "爱好")
    private String hobbies;

    @Schema(description = "喜欢的话题")
    private String favoriteTopics;

    @Schema(description = "喜欢的故事/绘本")
    private String favoriteStories;

    @Schema(description = "性格/偏好备注")
    private String personalityNote;

    @Schema(description = "学校/幼儿园")
    private String school;

    @Schema(description = "创建时间")
    private Date createTime;

    @Schema(description = "更新时间")
    private Date updateTime;
}
