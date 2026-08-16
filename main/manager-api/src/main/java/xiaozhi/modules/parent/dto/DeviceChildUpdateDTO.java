package xiaozhi.modules.parent.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonAlias;

@Data
@Schema(description = "更新设备主孩子")
public class DeviceChildUpdateDTO {

    @Schema(description = "孩子ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long childId;

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

    @Schema(description = "当前年级1-12")
    private Integer currentGrade;

    @Schema(description = "省/地区代码")
    @JsonAlias({"province", "provinceName"})
    private String provinceCode;

    @Schema(description = "地市编码")
    @JsonAlias({"city", "cityName"})
    private String cityCode;

    @Schema(description = "上下册 upper/lower")
    @JsonAlias({"volume", "semesterCode", "textbookVolume"})
    private String semester;

    @Schema(description = "教材版本代码")
    private String textbookEdition;

    @Schema(description = "教材系列")
    private String textbookSeries;

    @Schema(description = "启用学科JSON")
    private String subjectsEnabled;
}
