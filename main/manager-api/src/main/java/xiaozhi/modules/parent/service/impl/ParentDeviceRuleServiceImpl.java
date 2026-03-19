package xiaozhi.modules.parent.service.impl;

import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.device.service.DeviceService;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentDeviceRuleDao;
import xiaozhi.modules.parent.dto.ParentDeviceRuleAddDTO;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentDeviceRuleEntity;
import xiaozhi.modules.parent.service.ParentDeviceRuleService;
import xiaozhi.modules.parent.vo.ParentDeviceRuleVO;

/**
 * 家长设备规则服务实现
 */
@Service
@RequiredArgsConstructor
public class ParentDeviceRuleServiceImpl implements ParentDeviceRuleService {

    private static final int MAX_RULES_PER_DEVICE = 20;
    private static final int MAX_RULE_TEXT_LENGTH = 200;

    private final ParentDeviceRuleDao parentDeviceRuleDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final DeviceService deviceService;

    @Override
    public List<ParentDeviceRuleVO> listByDevice(Long parentUserId, String deviceId) {
        ensureDeviceBound(parentUserId, deviceId);
        List<ParentDeviceRuleEntity> list = parentDeviceRuleDao.selectList(
                new LambdaQueryWrapper<ParentDeviceRuleEntity>()
                        .eq(ParentDeviceRuleEntity::getDeviceId, deviceId)
                        .orderByDesc(ParentDeviceRuleEntity::getCreateTime));
        return list.stream().map(this::toVO).toList();
    }

    @Override
    public ParentDeviceRuleVO add(Long parentUserId, ParentDeviceRuleAddDTO dto) {
        String deviceId = dto != null ? dto.getDeviceId() : null;
        String ruleText = dto != null ? dto.getRuleText() : null;
        if (org.apache.commons.lang3.StringUtils.isBlank(deviceId) || org.apache.commons.lang3.StringUtils.isBlank(ruleText)) {
            throw new RenException("设备ID和规则内容不能为空");
        }
        ensureDeviceBound(parentUserId, deviceId);
        if (StringUtils.isBlank(ruleText) || ruleText.trim().length() > MAX_RULE_TEXT_LENGTH) {
            throw new RenException("规则内容不能为空，且不超过" + MAX_RULE_TEXT_LENGTH + "字");
        }
        long count = parentDeviceRuleDao.selectCount(
                new LambdaQueryWrapper<ParentDeviceRuleEntity>()
                        .eq(ParentDeviceRuleEntity::getDeviceId, deviceId));
        if (count >= MAX_RULES_PER_DEVICE) {
            throw new RenException("单个设备最多设置" + MAX_RULES_PER_DEVICE + "条规则");
        }
        String text = ruleText.trim();
        ParentDeviceRuleEntity entity = new ParentDeviceRuleEntity();
        entity.setDeviceId(deviceId);
        entity.setParentUserId(parentUserId);
        entity.setRuleText(text);
        entity.setCreateTime(new Date());
        parentDeviceRuleDao.insert(entity);
        return toVO(entity);
    }

    @Override
    public void delete(Long parentUserId, Long ruleId) {
        ParentDeviceRuleEntity entity = parentDeviceRuleDao.selectById(ruleId);
        if (entity == null) {
            throw new RenException("规则不存在");
        }
        ensureDeviceBound(parentUserId, entity.getDeviceId());
        parentDeviceRuleDao.deleteById(ruleId);
    }

    @Override
    public List<String> getRuleTextsByDeviceId(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return List.of();
        }
        List<ParentDeviceRuleEntity> list = parentDeviceRuleDao.selectList(
                new LambdaQueryWrapper<ParentDeviceRuleEntity>()
                        .eq(ParentDeviceRuleEntity::getDeviceId, deviceId)
                        .orderByDesc(ParentDeviceRuleEntity::getCreateTime));
        return list.stream()
                .map(ParentDeviceRuleEntity::getRuleText)
                .filter(StringUtils::isNotBlank)
                .toList();
    }

    @Override
    public ParentDeviceRuleVO addByMacAndParent(Long parentUserId, String macAddress, String ruleText) {
        DeviceEntity device = deviceService.getDeviceByMacAddress(macAddress.trim());
        if (device == null) {
            throw new RenException(ErrorCode.OTA_DEVICE_NOT_FOUND);
        }
        String deviceId = device.getId();
        ensureDeviceBound(parentUserId, deviceId);
        ParentDeviceRuleAddDTO dto = new ParentDeviceRuleAddDTO();
        dto.setDeviceId(deviceId);
        dto.setRuleText(ruleText);
        return add(parentUserId, dto);
    }

    @Override
    public boolean addByMacAddress(String agentId, String macAddress, String ruleText) {
        DeviceEntity device = deviceService.getDeviceByMacAddress(macAddress != null ? macAddress.trim() : "");
        if (device == null || org.apache.commons.lang3.StringUtils.isBlank(ruleText)) {
            return false;
        }
        String deviceId = device.getId();
        String norm = deviceId.replace(":", "_").toLowerCase();
        ParentDeviceBindingEntity binding = parentDeviceBindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                                .or().eq(ParentDeviceBindingEntity::getDeviceId, norm)));
        if (binding == null) return false;
        try {
            addByMacAndParent(binding.getParentUserId(), macAddress.trim(), ruleText.trim());
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void ensureDeviceBound(Long parentUserId, String deviceId) {
        ParentDeviceBindingEntity binding = parentDeviceBindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .eq(ParentDeviceBindingEntity::getDeviceId, deviceId));
        if (binding == null) {
            String normalized = deviceId.replace(":", "_").toLowerCase();
            binding = parentDeviceBindingDao.selectOne(
                    new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                            .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                            .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                                    .or().eq(ParentDeviceBindingEntity::getDeviceId, normalized)));
        }
        if (binding == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
    }

    private ParentDeviceRuleVO toVO(ParentDeviceRuleEntity e) {
        ParentDeviceRuleVO vo = new ParentDeviceRuleVO();
        vo.setId(e.getId());
        vo.setRuleText(e.getRuleText());
        vo.setCreateTime(e.getCreateTime());
        return vo;
    }
}
