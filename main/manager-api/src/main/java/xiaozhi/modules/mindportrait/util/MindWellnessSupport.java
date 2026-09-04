package xiaozhi.modules.mindportrait.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;

import xiaozhi.modules.mindportrait.entity.LearnerMindEvidenceEntity;
import xiaozhi.modules.mindportrait.vo.MindGraphVO;
import xiaozhi.modules.mindportrait.vo.MindNodeVO;
import xiaozhi.modules.mindportrait.vo.MindWellnessSummaryVO;

/**
 * 将 mp_* hub 节点聚合为家长端「四面向」心绪陪伴视图。
 */
public final class MindWellnessSupport {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String[] DAY_LABELS = {"一", "二", "三", "四", "五", "六", "日"};

    private static final List<WellnessDef> WELLNESS_DEFS = List.of(
            new WellnessDef("emotion", "情绪与状态", "🌤", Set.of("express", "stable")),
            new WellnessDef("stress", "面对压力时", "🌊", Set.of("recover")),
            new WellnessDef("relation", "与人相处", "🤝", Set.of("peer", "parent")),
            new WellnessDef("self", "自我感受", "🪞", Set.of("self")));

    private MindWellnessSupport() {
    }

    public static MindWellnessSummaryVO build(
            Long childId,
            String childName,
            MindGraphVO graph,
            List<LearnerMindEvidenceEntity> recentEvidence,
            Map<String, String> clusterByNodeCode) {
        MindWellnessSummaryVO vo = new MindWellnessSummaryVO();
        vo.setChildId(childId);
        vo.setChildName(StringUtils.defaultIfBlank(childName, "孩子"));

        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.plusDays(6);
        vo.setWeekStart(weekStart.format(ISO));
        vo.setWeekEnd(weekEnd.format(ISO));

        int observeDays = graph != null && graph.getRules() != null ? graph.getRules().getObserveDays() : 14;
        vo.setObserveDays(observeDays);

        List<MindNodeVO> hubs = graph == null ? List.of() : graph.getNodes().stream()
                .filter(n -> "hub".equals(n.getType()))
                .toList();

        Map<String, List<MindNodeVO>> hubsByCluster = hubs.stream()
                .collect(Collectors.groupingBy(h -> StringUtils.defaultString(h.getCluster())));

        List<MindWellnessSummaryVO.Dimension> dimensions = new ArrayList<>();
        boolean anyWatch = false;
        boolean anyConcern = false;

        for (WellnessDef def : WELLNESS_DEFS) {
            List<MindNodeVO> matched = def.clusters.stream()
                    .flatMap(c -> hubsByCluster.getOrDefault(c, List.of()).stream())
                    .toList();
            String status = aggregateStatus(matched);
            if ("watch".equals(status)) {
                anyWatch = true;
            }
            if ("stress".equals(def.code) && matched.stream().anyMatch(MindWellnessSupport::isConcernHub)) {
                anyConcern = true;
            }

            MindWellnessSummaryVO.Dimension dim = new MindWellnessSummaryVO.Dimension();
            dim.setCode(def.code);
            dim.setName(def.name);
            dim.setIcon(def.icon);
            dim.setStatus(status);
            dim.setStatusText(statusText(status));
            dim.setHint(buildHint(def, matched, status));
            dim.setDetail(buildDetail(def, matched, status));
            dimensions.add(dim);
        }
        vo.setDimensions(dimensions);

        String overallLevel = anyConcern ? "concern" : (anyWatch ? "watch" : "stable");
        vo.setOverallLevel(overallLevel);
        vo.setOverallText(buildOverallText(overallLevel, anyWatch));
        vo.setSummary(buildSummary(childName, dimensions, hubs, recentEvidence, clusterByNodeCode));
        vo.setChips(buildChips(overallLevel, observeDays, anyWatch, recentEvidence));
        vo.setWeekTrend(buildWeekTrend(recentEvidence, clusterByNodeCode, weekStart));

        boolean showActions = anyWatch || !"stable".equals(overallLevel);
        vo.setShowActions(showActions);
        if (showActions) {
            MindWellnessSummaryVO.Actions actions = new MindWellnessSummaryVO.Actions();
            MindWellnessSummaryVO.ActionItem chat = new MindWellnessSummaryVO.ActionItem();
            chat.setLabel("去会话看陪伴建议");
            MindWellnessSummaryVO.ActionItem detail = new MindWellnessSummaryVO.ActionItem();
            detail.setLabel("查看详情");
            actions.setChat(chat);
            actions.setDetail(detail);
            vo.setActions(actions);
        }
        return vo;
    }

    private static String aggregateStatus(List<MindNodeVO> hubs) {
        String worst = "ok";
        for (MindNodeVO hub : hubs) {
            worst = worseStatus(worst, hubStatus(hub));
        }
        return hubs.isEmpty() ? "observe" : worst;
    }

    private static String hubStatus(MindNodeVO hub) {
        if (hub == null) {
            return "observe";
        }
        String state = StringUtils.defaultString(hub.getState());
        String cluster = StringUtils.defaultString(hub.getCluster());
        int evidence = hub.getEvidenceCount();
        if ("locked".equals(state) || evidence < 2) {
            return "observe";
        }
        if ("recover".equals(cluster)) {
            if ("strong".equals(state) || ("visible".equals(state) && hub.getStrength() >= 55)) {
                return "watch";
            }
            return "observe";
        }
        if ("visible".equals(state) || "strong".equals(state)) {
            return "ok";
        }
        return "observe";
    }

    private static boolean isConcernHub(MindNodeVO hub) {
        return "recover".equals(hub.getCluster()) && "strong".equals(hub.getState());
    }

    private static String worseStatus(String a, String b) {
        int pa = statusPriority(a);
        int pb = statusPriority(b);
        return pa >= pb ? a : b;
    }

    private static int statusPriority(String status) {
        return switch (status) {
            case "watch" -> 2;
            case "observe" -> 1;
            default -> 0;
        };
    }

    private static String statusText(String status) {
        return switch (status) {
            case "watch" -> "需留意";
            case "observe" -> "观察中";
            default -> "平稳";
        };
    }

    private static String buildOverallText(String level, boolean anyWatch) {
        return switch (level) {
            case "concern" -> "近期压力信号较多，建议多陪伴";
            case "watch" -> "整体平稳，略有波动";
            default -> anyWatch ? "整体平稳，略有波动" : "整体平稳";
        };
    }

    private static String buildHint(WellnessDef def, List<MindNodeVO> hubs, String status) {
        if (hubs.isEmpty()) {
            return "证据仍在积累";
        }
        int evidence = hubs.stream().mapToInt(MindNodeVO::getEvidenceCount).sum();
        return switch (def.code) {
            case "emotion" -> status.equals("ok") ? "表达感受自然" : "情绪表达有波动";
            case "stress" -> evidence > 0 ? "相关话题本周 " + Math.max(1, evidence / 3) + " 次" : "未见明显压力话题";
            case "relation" -> status.equals("ok") ? "同伴与亲子沟通积极" : "互动信号需多观察";
            case "self" -> "证据仍在积累";
            default -> def.name;
        };
    }

    private static String buildDetail(WellnessDef def, List<MindNodeVO> hubs, String status) {
        if (hubs.isEmpty()) {
            return "基于日常对话的观察仍在积累中，建议继续自然聊天。";
        }
        String labels = hubs.stream().map(MindNodeVO::getLabel).distinct().collect(Collectors.joining("、"));
        int evidence = hubs.stream().mapToInt(MindNodeVO::getEvidenceCount).sum();
        return switch (status) {
            case "watch" -> "本周在「" + labels + "」相关对话中出现 "
                    + evidence + " 条观测信号，语气或主题值得温柔关心，不代表孩子「有问题」。";
            case "observe" -> "「" + labels + "」方向证据尚少（" + evidence
                    + " 条），系统仍在观察，建议保持日常闲聊。";
            default -> "本周在「" + labels + "」相关表达整体自然，共 " + evidence + " 条观测信号。";
        };
    }

    private static String buildSummary(
            String childName,
            List<MindWellnessSummaryVO.Dimension> dimensions,
            List<MindNodeVO> hubs,
            List<LearnerMindEvidenceEntity> recentEvidence,
            Map<String, String> clusterByNodeCode) {
        String name = StringUtils.defaultIfBlank(childName, "孩子");
        long stressEvidence = countClusterEvidence(recentEvidence, clusterByNodeCode, "recover");
        MindWellnessSummaryVO.Dimension stress = dimensions.stream()
                .filter(d -> "stress".equals(d.getCode())).findFirst().orElse(null);
        if (stress != null && "watch".equals(stress.getStatus())) {
            return "基于日常对话：" + name + "多数时候情绪表达自然；压力相关话题本周出现 "
                    + Math.max(1, stressEvidence) + " 次信号，谈及时可能略紧张，建议结合趋势温柔关心。";
        }
        if (hubs.stream().allMatch(h -> "locked".equals(h.getState()) || h.getEvidenceCount() < 2)) {
            return "基于日常对话：观察数据仍在积累中，请继续让孩子与小智自然聊天。";
        }
        return "基于日常对话：" + name + "近期情绪表达整体自然，各生活面向未见明显困扰信号。";
    }

    private static long countClusterEvidence(
            List<LearnerMindEvidenceEntity> evidence,
            Map<String, String> clusterByNodeCode,
            String cluster) {
        return evidence.stream()
                .filter(e -> cluster.equals(clusterByNodeCode.get(e.getNodeCode())))
                .count();
    }

    private static List<MindWellnessSummaryVO.Chip> buildChips(
            String overallLevel,
            int observeDays,
            boolean anyWatch,
            List<LearnerMindEvidenceEntity> recentEvidence) {
        List<MindWellnessSummaryVO.Chip> chips = new ArrayList<>();
        chips.add(chip("观察 " + observeDays + " 天", "neutral"));
        if ("stable".equals(overallLevel) && !anyWatch) {
            chips.add(chip("无需紧急关注", "ok"));
        } else if (anyWatch) {
            chips.add(chip("压力需留意", "watch"));
        }
        chips.add(chip(recentEvidence.size() >= 5 ? "比上周更稳定" : "持续观察中", "ok"));
        return chips;
    }

    private static MindWellnessSummaryVO.Chip chip(String text, String type) {
        MindWellnessSummaryVO.Chip c = new MindWellnessSummaryVO.Chip();
        c.setText(text);
        c.setType(type);
        return c;
    }

    private static List<MindWellnessSummaryVO.DayTrend> buildWeekTrend(
            List<LearnerMindEvidenceEntity> evidence,
            Map<String, String> clusterByNodeCode,
            LocalDate weekStart) {
        Map<LocalDate, Long> stressByDay = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) {
            stressByDay.put(weekStart.plusDays(i), 0L);
        }
        ZoneId zone = ZoneId.systemDefault();
        for (LearnerMindEvidenceEntity e : evidence) {
            if (e.getCreateTime() == null) {
                continue;
            }
            LocalDate d = e.getCreateTime().toInstant().atZone(zone).toLocalDate();
            if (!stressByDay.containsKey(d)) {
                continue;
            }
            if ("recover".equals(clusterByNodeCode.get(e.getNodeCode()))) {
                stressByDay.merge(d, 1L, Long::sum);
            }
        }
        List<MindWellnessSummaryVO.DayTrend> trend = new ArrayList<>();
        int i = 0;
        for (Map.Entry<LocalDate, Long> entry : stressByDay.entrySet()) {
            MindWellnessSummaryVO.DayTrend day = new MindWellnessSummaryVO.DayTrend();
            day.setDate(entry.getKey().format(ISO));
            day.setDayLabel(DAY_LABELS[i]);
            day.setLevel(entry.getValue() >= 2 ? "watch" : (entry.getValue() > 0 ? "neutral" : "ok"));
            trend.add(day);
            i++;
        }
        return trend;
    }

    public static Date weekStartDate(LocalDate weekStart) {
        return Date.from(weekStart.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    public static LocalDate parseWeekStart(String weekStart) {
        return weekStart != null
                ? LocalDate.parse(weekStart, ISO)
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private record WellnessDef(String code, String name, String icon, Set<String> clusters) {
    }
}
