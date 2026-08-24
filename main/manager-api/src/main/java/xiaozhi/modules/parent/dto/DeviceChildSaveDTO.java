package xiaozhi.modules.parent.dto;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import com.fasterxml.jackson.annotation.JsonAlias;

@Data
@Schema(description = "保存/添加设备主孩子")
public class DeviceChildSaveDTO {

    @Schema(description = "设备ID（必填）", requiredMode = Schema.RequiredMode.REQUIRED)
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

    @Schema(description = "当前年级：0=幼小衔接3-6岁，1-12=小学及以上")
    private Integer currentGrade;

    @Schema(description = "省/地区代码，与教研一致")
    @JsonAlias({"province", "provinceName"})
    private String provinceCode;

    @Schema(description = "地市编码，如 shandong_jinan")
    @JsonAlias({"city", "cityName"})
    private String cityCode;

    @Schema(description = "上下册 upper=上册 lower=下册")
    @JsonAlias({"volume", "semesterCode", "textbookVolume"})
    private String semester;

    @Schema(description = "教材版本代码 pep/generic")
    private String textbookEdition;

    @Schema(description = "教材系列（兼容旧版，建议改用 textbookEdition）")
    private String textbookSeries;

    @Schema(description = "启用学科JSON")
    private String subjectsEnabled;
}
