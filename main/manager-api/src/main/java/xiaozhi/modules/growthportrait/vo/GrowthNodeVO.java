package xiaozhi.modules.growthportrait.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "成长星图节点")
public class GrowthNodeVO {

    private String id;
    private String type;
    private String label;
    private String shortLabel;
    private String shortDesc;
    private String cluster;
    private int level;
    private int strength;
    private int evidenceCount;
    private int requiredCount;
    private String state;
    private double visualIntensity;
    private String visualTier;
    private String parentHub;
    private String parentSub;
    private String evidence;
    private String suggest;
    /** 可选：服务端预布局（0~1），小程序 canvas 可直接用 */
    private Double x;
    private Double y;
}
