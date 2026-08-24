package xiaozhi.modules.parent.service.impl;

import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.modules.agent.service.AgentVoicePrintService;
import xiaozhi.modules.learning.util.ChildGradeOptionsUtil;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dto.DeviceChildSaveDTO;
import xiaozhi.modules.parent.dto.DeviceChildUpdateDTO;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.service.DeviceChildService;
import xiaozhi.modules.parent.util.ParentDeviceAccessHelper;
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
        ParentDeviceAccessHelper.requireOwnerWrite(parentDeviceBindingDao, parentUserId, dto.getDeviceId());
        DeviceChildEntity existing = deviceChildDao.selectOne(
                new LambdaQueryWrapper<DeviceChildEntity>()
                        .eq(DeviceChildEntity::getDeviceId, dto.getDeviceId()));
        Date now = new Date();
        if (existing != null) {
            copyDtoToEntity(dto, existing);
            ChildGradeOptionsUtil.normalizeGradeProfile(existing);
            existing.setUpdateTime(now);
            deviceChildDao.updateById(existing);
            return toVo(existing);
        }
        DeviceChildEntity entity = ConvertUtils.sourceToTarget(dto, DeviceChildEntity.class);
        ChildGradeOptionsUtil.normalizeGradeProfile(entity);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        deviceChildDao.insert(entity);
        return toVo(entity);
    }

    @Override
    public DeviceChildVO getByDeviceId(Long parentUserId, String deviceId) {
        ParentDeviceAccessHelper.requireActiveBinding(parentDeviceBindingDao, parentUserId, deviceId);
        DeviceChildEntity entity = ParentDeviceAccessHelper.findDeviceChild(deviceChildDao, deviceId);
        return entity != null ? toVo(entity) : null;
    }

    @Override
    public void update(Long parentUserId, DeviceChildUpdateDTO dto) {
        DeviceChildEntity entity = deviceChildDao.selectById(dto.getChildId());
        if (entity == null) {
            throw new RenException("孩子不存在");
        }
        ParentDeviceAccessHelper.requireOwnerWrite(parentDeviceBindingDao, parentUserId, entity.getDeviceId());
        copyUpdateDtoToEntity(dto, entity);
        ChildGradeOptionsUtil.normalizeGradeProfile(entity);
        entity.setUpdateTime(new Date());
        deviceChildDao.updateById(entity);
    }

    private DeviceChildVO toVo(DeviceChildEntity entity) {
        DeviceChildVO vo = ConvertUtils.sourceToTarget(entity, DeviceChildVO.class);
        ChildGradeOptionsUtil.enrichChildVo(entity, vo);
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByDeviceId(Long parentUserId, String deviceId) {
        ParentDeviceAccessHelper.requireOwnerWrite(parentDeviceBindingDao, parentUserId, deviceId);
        DeviceChildEntity child = deviceChildDao.selectOne(
                new LambdaQueryWrapper<DeviceChildEntity>()
                        .eq(DeviceChildEntity::getDeviceId, deviceId));
        if (child != null) {
            agentVoicePrintService.deleteByChildId(child.getId());
            deviceChildDao.deleteById(child.getId());
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
        entity.setCurrentGrade(dto.getCurrentGrade());
        entity.setProvinceCode(
                xiaozhi.modules.learning.util.LearningGeoConstants.normalizeProvince(dto.getProvinceCode()));
        if (dto.getCityCode() != null && !dto.getCityCode().isBlank()) {
            entity.setCityCode(
                    xiaozhi.modules.learning.util.LearningGeoConstants.normalizeCity(
                            entity.getProvinceCode(), dto.getCityCode()));
        } else if (entity.getProvinceCode() != null) {
            entity.setCityCode(entity.getProvinceCode() + "_all");
        }
        if (dto.getSemester() != null && !dto.getSemester().isBlank()) {
            entity.setSemester(
                    xiaozhi.modules.learning.util.LearningGeoConstants.normalizeSemester(dto.getSemester()));
        } else if (entity.getSemester() == null || entity.getSemester().isBlank()) {
            entity.setSemester(xiaozhi.modules.learning.util.LearningGeoConstants.SEMESTER_UPPER);
        }
        entity.setTextbookEdition(
                xiaozhi.modules.learning.util.LearningProfileConstants.textbookFromLegacySeries(
                        dto.getTextbookSeries(), dto.getTextbookEdition()));
        entity.setTextbookSeries(dto.getTextbookSeries());
        entity.setSubjectsEnabled(dto.getSubjectsEnabled());
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
        if (dto.getCurrentGrade() != null) entity.setCurrentGrade(dto.getCurrentGrade());
        if (dto.getProvinceCode() != null && !dto.getProvinceCode().isBlank()) {
            entity.setProvinceCode(
                    xiaozhi.modules.learning.util.LearningGeoConstants.normalizeProvince(
                            dto.getProvinceCode()));
        }
        if (dto.getCityCode() != null && !dto.getCityCode().isBlank()) {
            entity.setCityCode(
                    xiaozhi.modules.learning.util.LearningGeoConstants.normalizeCity(
                            entity.getProvinceCode(), dto.getCityCode()));
        } else if (dto.getProvinceCode() != null && !dto.getProvinceCode().isBlank()) {
            entity.setCityCode(entity.getProvinceCode() + "_all");
        }
        if (dto.getSemester() != null) {
            entity.setSemester(
                    xiaozhi.modules.learning.util.LearningGeoConstants.normalizeSemester(dto.getSemester()));
        }
        if (dto.getTextbookEdition() != null || dto.getTextbookSeries() != null) {
            entity.setTextbookEdition(
                    xiaozhi.modules.learning.util.LearningProfileConstants.textbookFromLegacySeries(
                            dto.getTextbookSeries() != null ? dto.getTextbookSeries() : entity.getTextbookSeries(),
                            dto.getTextbookEdition() != null ? dto.getTextbookEdition() : entity.getTextbookEdition()));
        }
        if (dto.getTextbookSeries() != null) entity.setTextbookSeries(dto.getTextbookSeries());
        if (dto.getSubjectsEnabled() != null) entity.setSubjectsEnabled(dto.getSubjectsEnabled());
    }
}
