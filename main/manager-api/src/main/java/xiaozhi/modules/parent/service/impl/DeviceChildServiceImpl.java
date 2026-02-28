package xiaozhi.modules.parent.service.impl;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.modules.agent.service.AgentVoicePrintService;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dto.DeviceChildSaveDTO;
import xiaozhi.modules.parent.dto.DeviceChildUpdateDTO;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.service.DeviceChildService;
import xiaozhi.modules.parent.vo.DeviceChildVO;

@Service
@RequiredArgsConstructor
public class DeviceChildServiceImpl implements DeviceChildService {

    private final DeviceChildDao deviceChildDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final AgentVoicePrintService agentVoicePrintService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeviceChildVO saveOrUpdate(Long parentUserId, DeviceChildSaveDTO dto) {
        ensureDeviceBoundToParent(parentUserId, dto.getDeviceId());
        DeviceChildEntity existing = deviceChildDao.selectOne(
                new LambdaQueryWrapper<DeviceChildEntity>()
                        .eq(DeviceChildEntity::getDeviceId, dto.getDeviceId()));
        Date now = new Date();
        if (existing != null) {
            copyDtoToEntity(dto, existing);
            existing.setUpdateTime(now);
            deviceChildDao.updateById(existing);
            return ConvertUtils.sourceToTarget(existing, DeviceChildVO.class);
        }
        DeviceChildEntity entity = ConvertUtils.sourceToTarget(dto, DeviceChildEntity.class);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        deviceChildDao.insert(entity);
        return ConvertUtils.sourceToTarget(entity, DeviceChildVO.class);
    }

    @Override
    public DeviceChildVO getByDeviceId(Long parentUserId, String deviceId) {
        ensureDeviceBoundToParent(parentUserId, deviceId);
        DeviceChildEntity entity = deviceChildDao.selectOne(
                new LambdaQueryWrapper<DeviceChildEntity>()
                        .eq(DeviceChildEntity::getDeviceId, deviceId));
        return entity != null ? ConvertUtils.sourceToTarget(entity, DeviceChildVO.class) : null;
    }

    @Override
    public void update(Long parentUserId, DeviceChildUpdateDTO dto) {
        DeviceChildEntity entity = deviceChildDao.selectById(dto.getChildId());
        if (entity == null) {
            throw new RenException("孩子不存在");
        }
        ensureDeviceBoundToParent(parentUserId, entity.getDeviceId());
        copyUpdateDtoToEntity(dto, entity);
        entity.setUpdateTime(new Date());
        deviceChildDao.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByDeviceId(Long parentUserId, String deviceId) {
        ensureDeviceBoundToParent(parentUserId, deviceId);
        DeviceChildEntity child = deviceChildDao.selectOne(
                new LambdaQueryWrapper<DeviceChildEntity>()
                        .eq(DeviceChildEntity::getDeviceId, deviceId));
        if (child != null) {
            agentVoicePrintService.deleteByChildId(child.getId());
            deviceChildDao.deleteById(child.getId());
        }
    }

    private void ensureDeviceBoundToParent(Long parentUserId, String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        ParentDeviceBindingEntity binding = parentDeviceBindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .eq(ParentDeviceBindingEntity::getDeviceId, deviceId));
        if (binding == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
    }

    private void copyDtoToEntity(DeviceChildSaveDTO dto, DeviceChildEntity entity) {
        entity.setName(dto.getName());
        entity.setAvatarUrl(dto.getAvatarUrl());
        entity.setBirthday(dto.getBirthday());
        entity.setGender(dto.getGender());
        entity.setAgeStage(dto.getAgeStage());
        entity.setHobbies(dto.getHobbies());
        entity.setFavoriteTopics(dto.getFavoriteTopics());
        entity.setFavoriteStories(dto.getFavoriteStories());
        entity.setPersonalityNote(dto.getPersonalityNote());
        entity.setSchool(dto.getSchool());
    }

    private void copyUpdateDtoToEntity(DeviceChildUpdateDTO dto, DeviceChildEntity entity) {
        if (dto.getName() != null) entity.setName(dto.getName());
        if (dto.getAvatarUrl() != null) entity.setAvatarUrl(dto.getAvatarUrl());
        if (dto.getBirthday() != null) entity.setBirthday(dto.getBirthday());
        if (dto.getGender() != null) entity.setGender(dto.getGender());
        if (dto.getAgeStage() != null) entity.setAgeStage(dto.getAgeStage());
        if (dto.getHobbies() != null) entity.setHobbies(dto.getHobbies());
        if (dto.getFavoriteTopics() != null) entity.setFavoriteTopics(dto.getFavoriteTopics());
        if (dto.getFavoriteStories() != null) entity.setFavoriteStories(dto.getFavoriteStories());
        if (dto.getPersonalityNote() != null) entity.setPersonalityNote(dto.getPersonalityNote());
        if (dto.getSchool() != null) entity.setSchool(dto.getSchool());
    }
}
