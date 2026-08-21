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

    public static String lightState(int strength, int evidenceCount, int visibleThreshold, int strongThreshold) {
        if (evidenceCount < 2) {
            return "locked";
        }
        if (strength >= strongThreshold) {
            return "strong";
        }
        if (strength >= visibleThreshold) {
            return "visible";
        }
        return "collecting";
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
