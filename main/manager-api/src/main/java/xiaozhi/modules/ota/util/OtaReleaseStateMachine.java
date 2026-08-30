package xiaozhi.modules.ota.util;

import org.apache.commons.lang3.StringUtils;

/**
 * Beta 回滚状态机：无「暂停」状态；仅 active / rolled_back / superseded。
 */
public final class OtaReleaseStateMachine {

    public static final String ACTIVE = "active";
    public static final String ROLLED_BACK = "rolled_back";
    public static final String SUPERSEDED = "superseded";

    public static final String CHANNEL_BETA = "beta";
    public static final String CHANNEL_STABLE = "stable";

    private OtaReleaseStateMachine() {
    }

    public static void assertCanRollback(String channel, String status) {
        if (!CHANNEL_BETA.equalsIgnoreCase(StringUtils.trimToEmpty(channel))) {
            throw new IllegalStateException("仅 beta 发布可回滚");
        }
        if (!ACTIVE.equalsIgnoreCase(StringUtils.trimToEmpty(status))) {
            throw new IllegalStateException("仅 active 发布可回滚");
        }
    }

    /**
     * 场景 A：尚无成功升级 → 只标记 rolled_back，不重激活旧发布。
     * 场景 B：已有成功升级 → 标记 rolled_back，并重激活 previous（或指定目标）。
     */
    public static RollbackPlan planRollback(String channel, String status, int successCount, Long previousReleaseId,
            Long explicitTargetReleaseId) {
        assertCanRollback(channel, status);
        boolean hasSuccess = successCount > 0;
        Long reactivateId = null;
        if (explicitTargetReleaseId != null) {
            reactivateId = explicitTargetReleaseId;
        } else if (hasSuccess && previousReleaseId != null) {
            reactivateId = previousReleaseId;
        }
        return new RollbackPlan(ROLLED_BACK, reactivateId, hasSuccess, !hasSuccess);
    }

    public record RollbackPlan(
            String newStatus,
            Long reactivateReleaseId,
            boolean hasSuccessfulUpgrades,
            boolean zeroCoverage) {
        public boolean shouldReactivatePrevious() {
            return reactivateReleaseId != null;
        }
    }
}
