package xiaozhi.modules.growthportrait.util;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import xiaozhi.modules.growthportrait.entity.GpTemplateNodeEntity;
import xiaozhi.modules.growthportrait.entity.LearnerGrowthStateEntity;

public final class GrowthStateEngine {

    private GrowthStateEngine() {
    }

    /**
     * 点亮状态：以证据条数为主（与前端进度条一致），strength 仅用于「强烈亮点」的质量加成。
     */
    public static String lightState(int strength, int evidenceCount, int requiredCount, int strongThreshold) {
        int target = normalizeRequired(requiredCount);
        if (evidenceCount < 2) {
            return "locked";
        }
        int strongByCount = (int) Math.ceil(target * 1.2);
        if (evidenceCount >= target
                && (evidenceCount >= strongByCount || strength >= normalizeStrongThreshold(strongThreshold))) {
            return "strong";
        }
        if (evidenceCount >= target) {
            return "visible";
        }
        return "collecting";
    }

    /** Hub 维度：状态由子能力汇聚，避免「证据满格但仍收集中」 */
    public static String rollupHubState(
            List<String> childCodes,
            Map<String, LearnerGrowthStateEntity> stateByCode,
            int evidenceCount,
            int requiredCount) {
        int target = normalizeRequired(requiredCount <= 0 ? 6 : requiredCount);
        if (evidenceCount < 2) {
            return "locked";
        }
        int strongChildren = 0;
        int visibleChildren = 0;
        for (String cc : childCodes) {
            LearnerGrowthStateEntity cs = stateByCode.get(cc);
            if (cs == null) {
                continue;
            }
            String s = String.valueOf(cs.getState());
            if ("strong".equals(s)) {
                strongChildren++;
            }
            if ("visible".equals(s) || "strong".equals(s)) {
                visibleChildren++;
            }
        }
        if (strongChildren > 0 && evidenceCount >= target) {
            return "strong";
        }
        if (evidenceCount >= target) {
            return "visible";
        }
        int childTotal = childCodes.size();
        if (childTotal > 0 && visibleChildren * 2 >= childTotal) {
            return "visible";
        }
        return "collecting";
    }

    public static String buildSuggest(String state, int evidenceCount, int requiredCount) {
        int target = normalizeRequired(requiredCount);
        return switch (String.valueOf(state)) {
            case "strong" -> "强烈亮点 · 可查看详情并安排亲子活动";
            case "visible" -> "倾向显现 · 继续观察同场景表现";
            case "collecting" -> {
                int remain = target - evidenceCount;
                yield "收集中 · 再积累 " + Math.max(1, remain) + " 条证据可「显现」";
            }
            default -> {
                int need = Math.max(0, 2 - evidenceCount);
                yield need > 0 ? "尚未解锁 · 再积累 " + need + " 条证据可开始观测" : "尚未解锁";
            }
        };
    }

    public static int computeStrength(int evidenceCount, int avgConfidence) {
        if (evidenceCount <= 0) {
            return 0;
        }
        double base = Math.min(100, evidenceCount * 12 + avgConfidence * 0.35);
        return (int) Math.round(Math.min(100, base));
    }

    public static void applyVisualHierarchy(
            List<GpTemplateNodeEntity> templateNodes,
            Map<String, LearnerGrowthStateEntity> stateByCode) {
        Map<String, GpTemplateNodeEntity> nodeByCode = new HashMap<>();
        templateNodes.forEach(n -> nodeByCode.put(n.getCode(), n));
        Map<String, Double> intensityByCode = new HashMap<>();
        List<GpTemplateNodeEntity> sorted = templateNodes.stream()
                .sorted(Comparator.comparingInt(n -> typeLevel(n.getNodeType())))
                .toList();
        for (GpTemplateNodeEntity tn : sorted) {
            LearnerGrowthStateEntity st = stateByCode.get(tn.getCode());
            if (st == null) {
                continue;
            }
            double typeCap = typeCap(tn.getNodeType());
            double stateBase = stateBase(st.getState());
            double v = stateBase * typeCap;
            GpTemplateNodeEntity parent = tn.getParentCode() != null ? nodeByCode.get(tn.getParentCode()) : null;
            if (parent != null && !"center".equals(parent.getNodeType())) {
                Double pi = intensityByCode.get(parent.getCode());
                if (pi != null) {
                    v = Math.min(v, pi * 0.92);
                }
            }
            st.setVisualIntensity(v);
            st.setVisualTier(visualTier(v));
            intensityByCode.put(tn.getCode(), v);
        }
    }

    private static int normalizeRequired(int requiredCount) {
        return Math.max(1, requiredCount <= 0 ? 3 : requiredCount);
    }

    private static int normalizeStrongThreshold(int strongThreshold) {
        return strongThreshold <= 0 ? 72 : strongThreshold;
    }

    private static int typeLevel(String type) {
        return switch (String.valueOf(type)) {
            case "hub" -> 1;
            case "sub" -> 2;
            case "signal" -> 3;
            default -> 9;
        };
    }

    private static double typeCap(String type) {
        return switch (String.valueOf(type)) {
            case "hub" -> 1.0;
            case "sub" -> 0.72;
            case "signal" -> 0.4;
            default -> 0.5;
        };
    }

    private static double stateBase(String state) {
        return switch (String.valueOf(state)) {
            case "strong" -> 1.0;
            case "visible" -> 0.52;
            case "collecting" -> 0.12;
            default -> 0.0;
        };
    }

    private static String visualTier(double v) {
        if (v >= 0.62) {
            return "high";
        }
        if (v >= 0.32) {
            return "mid";
        }
        if (v > 0.08) {
            return "low";
        }
        return "none";
    }
}
