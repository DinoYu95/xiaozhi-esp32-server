package xiaozhi.modules.risk.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.modules.risk.service.ChildRiskService;

/** 投递 outbox → 家长端「风险提示」列表 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChildRiskOutboxPoller {

    private final ChildRiskService childRiskService;

    @Scheduled(initialDelayString = "${risk.outbox.initial-delay-ms:5000}",
            fixedDelayString = "${risk.outbox.poll-ms:2000}")
    public void tick() {
        try {
            childRiskService.processPendingOutboxBatch();
        } catch (Exception e) {
            log.warn("risk outbox poller: {}", e.getMessage(), e);
        }
    }
}
