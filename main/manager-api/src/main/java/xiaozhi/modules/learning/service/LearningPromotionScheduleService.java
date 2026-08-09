package xiaozhi.modules.learning.service;

import java.util.List;

import xiaozhi.modules.learning.entity.LearningPromotionScheduleEntity;

public interface LearningPromotionScheduleService {

    List<LearningPromotionScheduleEntity> listAll();

    void saveOrUpdate(LearningPromotionScheduleEntity entity);

    /** 每日任务：为符合条件的孩子写入升学提醒 */
    void runDailyPromotionReminders();
}
