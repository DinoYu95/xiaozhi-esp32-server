package xiaozhi.modules.ota;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import xiaozhi.modules.ota.util.OtaReleaseStateMachine;

class OtaReleaseStateMachineTest {

    @Test
    void rejectNonBetaAndNonActive() {
        assertThrows(IllegalStateException.class,
                () -> OtaReleaseStateMachine.assertCanRollback("stable", "active"));
        assertThrows(IllegalStateException.class,
                () -> OtaReleaseStateMachine.assertCanRollback("beta", "superseded"));
        assertThrows(IllegalStateException.class,
                () -> OtaReleaseStateMachine.assertCanRollback("beta", "rolled_back"));
    }

    @Test
    void scenarioAZeroCoverageDoesNotReactivate() {
        OtaReleaseStateMachine.RollbackPlan plan = OtaReleaseStateMachine.planRollback(
                "beta", "active", 0, 100L, null);
        assertEquals(OtaReleaseStateMachine.ROLLED_BACK, plan.newStatus());
        assertTrue(plan.zeroCoverage());
        assertFalse(plan.hasSuccessfulUpgrades());
        assertFalse(plan.shouldReactivatePrevious());
        assertNull(plan.reactivateReleaseId());
    }

    @Test
    void scenarioBReactivatesPrevious() {
        OtaReleaseStateMachine.RollbackPlan plan = OtaReleaseStateMachine.planRollback(
                "beta", "active", 3, 88L, null);
        assertEquals(OtaReleaseStateMachine.ROLLED_BACK, plan.newStatus());
        assertTrue(plan.hasSuccessfulUpgrades());
        assertTrue(plan.shouldReactivatePrevious());
        assertEquals(88L, plan.reactivateReleaseId());
    }

    @Test
    void explicitTargetOverridesPrevious() {
        OtaReleaseStateMachine.RollbackPlan plan = OtaReleaseStateMachine.planRollback(
                "beta", "active", 5, 88L, 12L);
        assertEquals(12L, plan.reactivateReleaseId());
    }
}
