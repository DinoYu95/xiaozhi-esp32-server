package xiaozhi.modules.learning.task;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.modules.learning.service.LearningPromotionScheduleService;

@Component
@RequiredArgsConstructor
@Slf4j
public class GradePromotionReminderJob {

    private final LearningPromotionScheduleService promotionScheduleService;

    /** 每天 08:00（上海）扫描主孩子档案，写入升学前一日提醒 */
    @Scheduled(cron = "${learning.promotion-reminder.cron:0 0 8 * * ?}", zone = "Asia/Shanghai")
    public void run() {
        try {
            promotionScheduleService.runDailyPromotionReminders();
        } catch (Exception e) {
            log.warn("grade promotion reminder job failed: {}", e.getMessage());
        }
    }
}
