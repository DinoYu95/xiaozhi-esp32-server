package xiaozhi.modules.mindportrait.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import xiaozhi.modules.mindportrait.dto.TeachingMpPublishDTO;

/**
 * 心绪图谱默认模板（与教研 MindTemplateDefaults 结构一致），供首次启动自动发版。
 */
public final class MindDefaultTemplateBuilder {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final Map<String, String> AGE_LABELS = Map.of(
            "preschool", "幼儿 3–6 岁",
            "lower", "小学 1–2 年级",
            "upper", "小学 4 年级",
            "middle", "初中 2 年级");

    private static final List<String> MIND_DIMS = List.of(
            "express", "recover", "self", "peer", "parent", "stable");

    private static final Map<String, String> DIM_LABELS = Map.of(
            "express", "情绪表达",
            "recover", "压力恢复",
            "self", "自我感受",
            "peer", "同伴互动",
            "parent", "亲子沟通",
            "stable", "状态稳定");

    private static final List<String> SIGNAL_VERBS = List.of("对话中", "聊天时", "游戏时", "复盘时", "倾诉中", "互动中");
    private static final List<String> SIGNAL_DESC = List.of("主动表现", "反复出现", "跨场景", "稳定趋势", "细节到位", "持续投入");

    private MindDefaultTemplateBuilder() {
    }

    public static List<String> ageBands() {
        return List.copyOf(AGE_LABELS.keySet());
    }

    public static String normalizeAgeBand(String ageBand) {
        if (ageBand == null) {
            return "upper";
        }
        String b = ageBand.trim().toLowerCase();
        return AGE_LABELS.containsKey(b) ? b : "upper";
    }

    public static TeachingMpPublishDTO buildPublishBody(String ageBand) {
        String band = normalizeAgeBand(ageBand);
        TeachingMpPublishDTO body = new TeachingMpPublishDTO();
        body.setAgeBand(band);
        body.setVersionLabel("mp-bootstrap-" + band);
        body.setTeachingSubmissionId(null);
        body.setRulesJson(buildRulesJson(band));
        body.setNodes(new ArrayList<>());
        body.setEdges(new ArrayList<>());

        int sort = 0;
        for (String dim : MIND_DIMS) {
            String hubCode = "MIND." + band.toUpperCase() + ".HUB." + dim.toUpperCase();
            TeachingMpPublishDTO.Node hub = node(hubCode, "hub", null, DIM_LABELS.get(dim),
                    DIM_LABELS.get(dim), "心绪维度", dim, sort++, 6);
            body.getNodes().add(hub);

            List<String> subs = SUB_TEMPLATES.getOrDefault(dim, List.of("表现观察"));
            for (int si = 0; si < subs.size(); si++) {
                String subLabel = subs.get(si);
                String subCode = hubCode + ".SUB." + String.format("%03d", si + 1);
                TeachingMpPublishDTO.Node sub = node(subCode, "sub", hubCode, subLabel,
                        subLabel, "子维度", dim, si, 5);
                body.getNodes().add(sub);
                body.getEdges().add(edge(hubCode, subCode));

                for (int gi = 0; gi < 3; gi++) {
                    String sigCode = subCode + ".SIG." + String.format("%02d", gi + 1);
                    String sigDesc = SIGNAL_VERBS.get((si + gi) % SIGNAL_VERBS.size())
                            + " · " + SIGNAL_DESC.get(gi % SIGNAL_DESC.size());
                    TeachingMpPublishDTO.Node sig = node(sigCode, "signal", subCode, subLabel,
                            subLabel.length() > 5 ? subLabel.substring(0, 5) : subLabel,
                            sigDesc, dim, gi, 3);
                    sig.setMatchHints(List.of(subLabel, SIGNAL_DESC.get(gi % SIGNAL_DESC.size())));
                    body.getNodes().add(sig);
                    body.getEdges().add(edge(subCode, sigCode));
                }
            }
        }
        return body;
    }

    private static TeachingMpPublishDTO.Node node(String code, String type, String parentCode,
            String label, String shortLabel, String shortDesc, String cluster, int sortOrder,
            int requiredEvidence) {
        TeachingMpPublishDTO.Node n = new TeachingMpPublishDTO.Node();
        n.setCode(code);
        n.setNodeType(type);
        n.setParentCode(parentCode);
        n.setLabel(label);
        n.setShortLabel(shortLabel);
        n.setShortDesc(shortDesc);
        n.setClusterCode(cluster);
        n.setSortOrder(sortOrder);
        n.setRequiredEvidence(requiredEvidence);
        n.setVisibleThreshold(52);
        n.setStrongThreshold(72);
        return n;
    }

    private static TeachingMpPublishDTO.Edge edge(String from, String to) {
        TeachingMpPublishDTO.Edge e = new TeachingMpPublishDTO.Edge();
        e.setFromCode(from);
        e.setToCode(to);
        e.setEdgeType("CONTAINS");
        return e;
    }

    private static String buildRulesJson(String band) {
        try {
            ObjectNode root = JSON.createObjectNode();
            ObjectNode light = root.putObject("lightStates");
            light.putObject("locked").put("minEvidence", 0);
            light.putObject("collecting").put("minEvidence", 2);
            light.putObject("visible").put("strengthGte", 52);
            light.putObject("strong").put("strengthGte", 72);
            ObjectNode visual = root.putObject("visualHierarchy");
            visual.putObject("typeCap").put("hub", 1.0).put("sub", 0.72).put("signal", 0.4);
            visual.put("childOfParentFactor", 0.92);
            ObjectNode notify = root.putObject("notifyPolicy");
            notify.put("instantOnStrong", true);
            notify.put("weeklyInstantCap", 2);
            notify.put("weeklyDigestDay", "SUNDAY");
            root.put("observeDays", "preschool".equals(band) ? 21 : ("lower".equals(band) ? 14 : 10));
            return root.toString();
        } catch (Exception e) {
            return "{}";
        }
    }

    private static final Map<String, List<String>> SUB_TEMPLATES = buildSubTemplates();

    private static Map<String, List<String>> buildSubTemplates() {
        Map<String, List<String>> m = new LinkedHashMap<>();
        m.put("express", List.of("命名感受", "主动倾诉", "非暴力表达", "情绪识别", "表达需求", "描述心情"));
        m.put("recover", List.of("挫折恢复", "再尝试", "求助时机", "失败后平复", "换个方法", "短暂休息"));
        m.put("self", List.of("自我肯定", "接纳失败", "合理期待", "看到自己的好", "比较心态", "目标自觉"));
        m.put("peer", List.of("发起互动", "冲突修复", "被排斥应对", "合作分工", "共情回应", "轮流等待"));
        m.put("parent", List.of("分享日常", "表达需求", "边界沟通", "寻求安慰", "复述经历", "主动问候"));
        m.put("stable", List.of("睡眠提及", "情绪波动", "整体活力", "节奏稳定", "专注时长", "食欲提及"));
        return m;
    }
}
