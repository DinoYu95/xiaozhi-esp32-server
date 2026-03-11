package xiaozhi.modules.agent.service.impl;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.dao.ParentUserSkillDao;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.entity.ParentUserSkillEntity;
import xiaozhi.modules.agent.service.AgentParentSkillService;
import xiaozhi.modules.agent.vo.AdminParentUserSkillVO;

@Service
@RequiredArgsConstructor
public class AgentParentSkillServiceImpl implements AgentParentSkillService {

    private final ParentUserSkillDao parentUserSkillDao;
    private final ParentUserDao parentUserDao;

    @Override
    public List<AdminParentUserSkillVO> listAllForAdmin() {
        List<ParentUserSkillEntity> list = parentUserSkillDao.selectList(
                new LambdaQueryWrapper<ParentUserSkillEntity>()
                        .orderByDesc(ParentUserSkillEntity::getCreateTime));
        List<AdminParentUserSkillVO> result = ConvertUtils.sourceToTarget(list, AdminParentUserSkillVO.class);
        if (result.isEmpty()) {
            return result;
        }
        List<Long> parentUserIds = list.stream()
                .map(ParentUserSkillEntity::getParentUserId)
                .distinct()
                .collect(Collectors.toList());
        List<ParentUserEntity> users = parentUserDao.selectBatchIds(parentUserIds);
        Map<Long, String> nicknameMap = users.stream()
                .collect(Collectors.toMap(ParentUserEntity::getId, u -> {
                    if (u.getNickname() != null && !u.getNickname().trim().isEmpty()) {
                        return u.getNickname().trim();
                    }
                    return "家长#" + u.getId();
                }, (a, b) -> a));
        for (AdminParentUserSkillVO vo : result) {
            vo.setParentNickname(nicknameMap.getOrDefault(vo.getParentUserId(), "家长#" + vo.getParentUserId()));
        }
        return result;
    }

    @Override
    public void deleteByAdmin(Long id) {
        parentUserSkillDao.deleteById(id);
    }
}
