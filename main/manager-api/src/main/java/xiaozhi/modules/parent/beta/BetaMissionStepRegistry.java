package xiaozhi.modules.parent.beta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import lombok.Getter;

/**
 * 一期写死的 8 步任务注册表（非 DB）。
 */
public final class BetaMissionStepRegistry {

    public static final String CAMPAIGN_CODE = "beta_core_v1";
    public static final String CAMPAIGN_TITLE = "核心能力体验";
    public static final String CAMPAIGN_DESCRIPTION = "约 10 分钟熟悉设备、对话与安全能力";

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_COMPLETED = "completed";
    public static final String STATUS_SKIPPED = "skipped";

    public static final String VERIFY_AUTO = "auto";
    public static final String VERIFY_VISIT = "visit";
    public static final String NAVIGATE_TO = "navigateTo";

    private static final String QUERY_PREFIX = "from=beta_mission&stepKey=";

    private static final List<StepDef> STEPS;
    private static final Map<String, StepDef> STEP_BY_KEY;

    static {
        List<StepDef> list = new ArrayList<>();
        list.add(new StepDef("bind_device", "A", "设备与对话", "绑定第一台设备",
                "将第一台小智设备绑定到您的账号", true, VERIFY_AUTO, false,
                "/pages/add-device/add-device", false, false, false));
        list.add(new StepDef("has_child", "A", "设备与对话", "添加设备主孩子",
                "为设备设置主孩子，便于个性化对话", true, VERIFY_AUTO, false,
                "/pages/device-main-child/device-main-child", true, false, false));
        list.add(new StepDef("voiceprint_done", "A", "设备与对话", "为体验孩子录制声纹",
                "录制声纹后设备可识别体验孩子", false, VERIFY_AUTO, true,
                "/pages/voiceprint/voiceprint", false, true, true));
        list.add(new StepDef("skill_created", "A", "设备与对话", "添加一条对话能力",
                "创建一条自定义对话能力", true, VERIFY_AUTO, false,
                "/pages/create-skill/create-skill", false, false, false));
        list.add(new StepDef("risk_preference_set", "B", "安全与反馈", "设置风险关注侧重",
                "选择您更关注的风险领域", false, VERIFY_AUTO, true,
                "/pages/risk-watch/index", false, true, false));
        list.add(new StepDef("risk_watch_created", "B", "安全与反馈", "提交一条风险观察",
                "提交您希望关注的风险观察", false, VERIFY_AUTO, true,
                "/pages/risk-watch/create-type", false, true, false));
        list.add(new StepDef("risk_alert_viewed", "B", "安全与反馈", "查看风险告警入口",
                "了解风险告警列表入口", false, VERIFY_VISIT, false,
                "/pages/risk-alerts/risk-alerts", false, false, false));
        list.add(new StepDef("feedback_submitted", "B", "安全与反馈", "提交一条内测反馈",
                "反馈使用中的问题或建议", true, VERIFY_AUTO, false,
                "/pages/feedback/submit", false, false, false));
        STEPS = Collections.unmodifiableList(list);
        Map<String, StepDef> map = new LinkedHashMap<>();
        for (StepDef s : list) {
            map.put(s.getStepKey(), s);
        }
        STEP_BY_KEY = Collections.unmodifiableMap(map);
    }

    private BetaMissionStepRegistry() {
    }

    public static List<StepDef> allSteps() {
        return STEPS;
    }

    public static Optional<StepDef> find(String stepKey) {
        if (stepKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(STEP_BY_KEY.get(stepKey));
    }

    public static int requiredCount() {
        return (int) STEPS.stream().filter(StepDef::isRequired).count();
    }

    public static List<StepDef> autoSteps() {
        return STEPS.stream().filter(s -> VERIFY_AUTO.equals(s.getVerifyMode())).toList();
    }

    public static String buildActionUrl(
            StepDef step,
            Long contextChildId,
            String earliestDeviceId,
            String contextChildDeviceId) {
        if (step.isNeedsContextChild() && contextChildId == null) {
            return null;
        }
        StringBuilder url = new StringBuilder(step.getPagePath()).append('?').append(QUERY_PREFIX)
                .append(step.getStepKey());
        if (step.isAppendDeviceIdFromEarliestBinding()) {
            if (StringUtils.isBlank(earliestDeviceId)) {
                return null;
            }
            url.append("&deviceId=").append(earliestDeviceId);
        }
        if (step.isAppendDeviceIdFromContextChild()) {
            if (StringUtils.isBlank(contextChildDeviceId)) {
                return null;
            }
            url.append("&deviceId=").append(contextChildDeviceId);
            url.append("&childId=").append(contextChildId);
        } else if (step.isAppendChildId()) {
            url.append("&childId=").append(contextChildId);
        }
        return url.toString();
    }

    @Getter
    public static final class StepDef {
        private final String stepKey;
        private final String section;
        private final String sectionTitle;
        private final String title;
        private final String description;
        private final boolean required;
        private final String verifyMode;
        private final boolean needsContextChild;
        private final String pagePath;
        private final boolean appendDeviceIdFromEarliestBinding;
        private final boolean appendDeviceIdFromContextChild;
        private final boolean appendChildId;

        StepDef(String stepKey, String section, String sectionTitle, String title, String description,
                boolean required, String verifyMode, boolean needsContextChild, String pagePath,
                boolean appendDeviceIdFromEarliestBinding, boolean appendDeviceIdFromContextChild,
                boolean appendChildId) {
            this.stepKey = stepKey;
            this.section = section;
            this.sectionTitle = sectionTitle;
            this.title = title;
            this.description = description;
            this.required = required;
            this.verifyMode = verifyMode;
            this.needsContextChild = needsContextChild;
            this.pagePath = pagePath;
            this.appendDeviceIdFromEarliestBinding = appendDeviceIdFromEarliestBinding;
            this.appendDeviceIdFromContextChild = appendDeviceIdFromContextChild;
            this.appendChildId = appendChildId;
        }
    }
}
