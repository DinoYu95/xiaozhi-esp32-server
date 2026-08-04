package xiaozhi.modules.learning.service;

import xiaozhi.modules.learning.vo.LearningMasteryMapVO;
import xiaozhi.modules.learning.vo.LearningModulePathVO;
import xiaozhi.modules.learning.vo.LearningSkillDetailVO;

public interface LearningMasteryService {

    LearningMasteryMapVO masteryMap(Long parentUserId, Long childId, String subject, Integer grade);

    LearningSkillDetailVO skillDetail(Long parentUserId, Long childId, String skillCode);

    LearningModulePathVO modulePath(
            Long parentUserId, Long childId, String subject, Integer grade, String moduleKey);
}
