package xiaozhi.modules.parent.service;

import xiaozhi.modules.parent.vo.DailyBriefVO;

/**
 * 主孩子今日简报服务
 */
public interface DailyBriefService {

    /**
     * 获取主孩子的今日简报（结构化统计，供前端模板渲染）
     *
     * @param parentUserId 家长用户 ID
     * @param childId      孩子 ID（device_child.id）
     * @return 今日简报，无数据时 messageCount=0、highlights 为空
     */
    DailyBriefVO getDailyBrief(Long parentUserId, Long childId);
}
