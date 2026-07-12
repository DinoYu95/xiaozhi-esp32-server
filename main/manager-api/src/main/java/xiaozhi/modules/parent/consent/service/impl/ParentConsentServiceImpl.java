package xiaozhi.modules.parent.consent.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.modules.parent.consent.dao.ParentConsentDocumentDao;
import xiaozhi.modules.parent.consent.dao.ParentConsentRecordDao;
import xiaozhi.modules.parent.consent.dto.ParentConsentAdminPublishDTO;
import xiaozhi.modules.parent.consent.dto.ParentConsentAdminSettingsDTO;
import xiaozhi.modules.parent.consent.dto.ParentConsentAgreeDTO;
import xiaozhi.modules.parent.consent.entity.ParentConsentDocumentEntity;
import xiaozhi.modules.parent.consent.entity.ParentConsentRecordEntity;
import xiaozhi.modules.parent.consent.service.ParentConsentService;
import xiaozhi.modules.parent.consent.vo.ParentConsentAdminOverviewVO;
import xiaozhi.modules.parent.consent.vo.ParentConsentDocumentVO;
import xiaozhi.modules.parent.consent.vo.ParentConsentHistoryItemVO;
import xiaozhi.modules.parent.consent.vo.ParentConsentPendingUserVO;
import xiaozhi.modules.parent.consent.vo.ParentConsentStatusVO;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.util.ParentDeviceAccessHelper;
import xiaozhi.modules.sys.service.SysParamsService;

@Service
@RequiredArgsConstructor
public class ParentConsentServiceImpl implements ParentConsentService {

    private static final String PARAM_ENABLED = "parent.consent.enabled";
    private static final String PARAM_DEVICE_BLOCK_MODE = "parent.consent.device_block_mode";
    private static final String PARAM_DEVICE_BLOCKED_PROMPT = "consent_blocked.prompt";
    private static final String PARAM_RETENTION_DAYS = "parent.consent.retention_days_display";
    private static final String MODE_OWNER_ONLY = "owner_only";
    private static final String MODE_ALL_MEMBERS = "all_members";
    private static final String DEFAULT_DEVICE_BLOCKED_PROMPT =
            "请先由主账号家长在小程序中阅读并同意儿童隐私保护说明。同意后设备才能继续使用，本次对话即将结束。";

    private final ParentConsentDocumentDao parentConsentDocumentDao;
    private final ParentConsentRecordDao parentConsentRecordDao;
    private final ParentUserDao parentUserDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final SysParamsService sysParamsService;

    @Override
    public ParentConsentDocumentVO getPublishedDocument() {
        ParentConsentDocumentEntity doc = findPublished();
        if (doc == null) {
            return null;
        }
        return toDocumentVo(doc);
    }

    @Override
    public ParentConsentStatusVO getStatus(Long parentUserId) {
        ParentConsentStatusVO vo = new ParentConsentStatusVO();
        boolean enabled = isConsentEnabled();
        vo.setConsentEnabled(enabled);
        vo.setDeviceBlockMode(getDeviceBlockMode());
        ParentConsentDocumentEntity doc = findPublished();
        if (!enabled || doc == null) {
            vo.setConsentRequired(false);
            vo.setBlocking(false);
            return vo;
        }
        vo.setCurrentVersion(doc.getVersion());
        vo.setTitle(doc.getTitle());
        vo.setSummary(doc.getSummary());
        ParentConsentRecordEntity record = findAgreedRecord(parentUserId, doc.getVersion());
        if (record != null) {
            vo.setAgreedVersion(record.getVersion());
            vo.setAgreedAt(record.getAgreedAt());
            vo.setConsentRequired(false);
            vo.setBlocking(false);
        } else {
            vo.setConsentRequired(true);
            vo.setBlocking(true);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void agree(Long parentUserId, ParentConsentAgreeDTO dto, String clientIp, String userAgent) {
        if (!isConsentEnabled()) {
            return;
        }
        ParentConsentDocumentEntity doc = findPublished();
        if (doc == null) {
            throw new RenException(ErrorCode.PARENT_CONSENT_VERSION_INVALID);
        }
        String version = StringUtils.trimToEmpty(dto.getVersion());
        if (!StringUtils.equals(version, doc.getVersion())) {
            throw new RenException(ErrorCode.PARENT_CONSENT_VERSION_INVALID);
        }
        if (findAgreedRecord(parentUserId, version) != null) {
            return;
        }
        ParentConsentRecordEntity row = new ParentConsentRecordEntity();
        row.setParentUserId(parentUserId);
        row.setVersion(version);
        row.setAgreedAt(new Date());
        row.setChannel(ParentConsentRecordEntity.CHANNEL_WECHAT_MINIPROGRAM);
        row.setClientIp(StringUtils.left(StringUtils.trimToNull(clientIp), 64));
        row.setUserAgent(StringUtils.left(StringUtils.trimToNull(userAgent), 512));
        try {
            parentConsentRecordDao.insert(row);
        } catch (DuplicateKeyException ex) {
            // 并发幂等
        }
    }

    @Override
    public boolean isConsentEnabled() {
        String v = sysParamsService.getValue(PARAM_ENABLED, true);
        return "true".equalsIgnoreCase(StringUtils.trimToEmpty(v));
    }

    @Override
    public boolean isConsentRequired(Long parentUserId) {
        if (!isConsentEnabled() || parentUserId == null) {
            return false;
        }
        ParentConsentDocumentEntity doc = findPublished();
        if (doc == null) {
            return false;
        }
        return findAgreedRecord(parentUserId, doc.getVersion()) == null;
    }

    @Override
    public boolean hasAgreedCurrentVersion(Long parentUserId) {
        return !isConsentRequired(parentUserId);
    }

    @Override
    public boolean isDeviceConsentOk(String deviceId, String macAddress) {
        if (!isConsentEnabled()) {
            return true;
        }
        ParentConsentDocumentEntity doc = findPublished();
        if (doc == null) {
            return true;
        }
        String currentVersion = doc.getVersion();
        List<ParentDeviceBindingEntity> bindings = resolveDeviceBindings(deviceId, macAddress);
        if (bindings.isEmpty()) {
            return true;
        }
        if (MODE_ALL_MEMBERS.equalsIgnoreCase(getDeviceBlockMode())) {
            for (ParentDeviceBindingEntity binding : bindings) {
                if (binding.getParentUserId() == null) {
                    continue;
                }
                if (findAgreedRecord(binding.getParentUserId(), currentVersion) == null) {
                    return false;
                }
            }
            return true;
        }
        ParentDeviceBindingEntity owner = findPrimaryOwnerForBindings(bindings, deviceId, macAddress);
        if (owner == null || owner.getParentUserId() == null) {
            return false;
        }
        return findAgreedRecord(owner.getParentUserId(), currentVersion) != null;
    }

    @Override
    public String getDeviceBlockedPrompt() {
        String v = sysParamsService.getValue(PARAM_DEVICE_BLOCKED_PROMPT, true);
        if (StringUtils.isNotBlank(v)) {
            return v.trim();
        }
        return DEFAULT_DEVICE_BLOCKED_PROMPT;
    }

    @Override
    public ParentConsentAdminOverviewVO adminOverview() {
        ParentConsentDocumentEntity doc = findPublished();
        ParentConsentAdminOverviewVO vo = new ParentConsentAdminOverviewVO();
        vo.setEnabled(isConsentEnabled());
        vo.setDeviceBlockMode(getDeviceBlockMode());
        vo.setDeviceBlockedPrompt(getDeviceBlockedPrompt());
        vo.setRetentionDaysDisplay(getRetentionDaysDisplay());
        if (doc != null) {
            vo.setCurrentVersion(doc.getVersion());
            vo.setTitle(doc.getTitle());
            vo.setSummary(doc.getSummary());
            vo.setContent(doc.getContent());
            vo.setPublishedAt(doc.getPublishedAt());
        }
        int total = countParentUsers();
        int agreed = doc != null ? countAgreedForVersion(doc.getVersion()) : 0;
        vo.setParentUserTotal(total);
        vo.setAgreedCurrentCount(agreed);
        vo.setPendingCount(Math.max(0, total - agreed));
        return vo;
    }

    @Override
    public void adminSaveSettings(ParentConsentAdminSettingsDTO dto) {
        if (dto.getEnabled() != null) {
            sysParamsService.updateValueByCode(PARAM_ENABLED, dto.getEnabled() ? "true" : "false");
        }
        if (StringUtils.isNotBlank(dto.getDeviceBlockMode())) {
            String mode = dto.getDeviceBlockMode().trim().toLowerCase(Locale.ROOT);
            if (!MODE_OWNER_ONLY.equals(mode) && !MODE_ALL_MEMBERS.equals(mode)) {
                throw new RenException("deviceBlockMode 仅支持 owner_only 或 all_members");
            }
            sysParamsService.updateValueByCode(PARAM_DEVICE_BLOCK_MODE, mode);
        }
        if (dto.getDeviceBlockedPrompt() != null) {
            sysParamsService.updateValueByCode(PARAM_DEVICE_BLOCKED_PROMPT, dto.getDeviceBlockedPrompt().trim());
        }
        if (dto.getRetentionDaysDisplay() != null) {
            sysParamsService.updateValueByCode(PARAM_RETENTION_DAYS, String.valueOf(dto.getRetentionDaysDisplay()));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminPublish(ParentConsentAdminPublishDTO dto) {
        String title = StringUtils.trimToEmpty(dto.getTitle());
        String summary = StringUtils.trimToEmpty(dto.getSummary());
        String content = StringUtils.trimToEmpty(dto.getContent());
        if (StringUtils.isBlank(title) || StringUtils.isBlank(summary) || StringUtils.isBlank(content)) {
            throw new RenException("标题、摘要、正文均不能为空");
        }
        ParentConsentDocumentEntity current = findPublished();
        if (current != null) {
            parentConsentDocumentDao.update(null, new LambdaUpdateWrapper<ParentConsentDocumentEntity>()
                    .eq(ParentConsentDocumentEntity::getId, current.getId())
                    .set(ParentConsentDocumentEntity::getStatus, ParentConsentDocumentEntity.STATUS_ARCHIVED)
                    .set(ParentConsentDocumentEntity::getUpdateTime, new Date()));
        }
        String newVersion = nextVersionString();
        Date now = new Date();
        ParentConsentDocumentEntity published = new ParentConsentDocumentEntity();
        published.setVersion(newVersion);
        published.setTitle(title);
        published.setSummary(summary);
        published.setContent(content);
        published.setStatus(ParentConsentDocumentEntity.STATUS_PUBLISHED);
        published.setPublishedAt(now);
        published.setCreateTime(now);
        published.setUpdateTime(now);
        parentConsentDocumentDao.insert(published);
    }

    @Override
    public List<ParentConsentHistoryItemVO> adminHistory() {
        List<ParentConsentDocumentEntity> list = parentConsentDocumentDao.selectList(
                new LambdaQueryWrapper<ParentConsentDocumentEntity>()
                        .orderByDesc(ParentConsentDocumentEntity::getPublishedAt)
                        .orderByDesc(ParentConsentDocumentEntity::getId));
        List<ParentConsentHistoryItemVO> result = new ArrayList<>();
        for (ParentConsentDocumentEntity e : list) {
            ParentConsentHistoryItemVO vo = new ParentConsentHistoryItemVO();
            vo.setVersion(e.getVersion());
            vo.setTitle(e.getTitle());
            vo.setStatus(e.getStatus());
            vo.setPublishedAt(e.getPublishedAt());
            vo.setUpdateTime(e.getUpdateTime());
            result.add(vo);
        }
        return result;
    }

    @Override
    public PageData<ParentConsentPendingUserVO> adminPendingUsers(Map<String, Object> params) {
        ParentConsentDocumentEntity doc = findPublished();
        if (doc == null) {
            return new PageData<>(List.of(), 0);
        }
        int page = parseInt(params, "page", 1);
        int limit = parseInt(params, "limit", 20);
        Set<Long> agreedIds = parentConsentRecordDao.selectList(
                new LambdaQueryWrapper<ParentConsentRecordEntity>()
                        .eq(ParentConsentRecordEntity::getVersion, doc.getVersion()))
                .stream()
                .map(ParentConsentRecordEntity::getParentUserId)
                .collect(Collectors.toSet());
        LambdaQueryWrapper<ParentUserEntity> q = new LambdaQueryWrapper<ParentUserEntity>()
                .orderByDesc(ParentUserEntity::getId);
        if (!agreedIds.isEmpty()) {
            q.notIn(ParentUserEntity::getId, agreedIds);
        }
        Page<ParentUserEntity> pg = parentUserDao.selectPage(new Page<>(page, limit), q);
        List<ParentConsentPendingUserVO> list = new ArrayList<>();
        for (ParentUserEntity u : pg.getRecords()) {
            ParentConsentPendingUserVO vo = new ParentConsentPendingUserVO();
            vo.setParentUserId(u.getId());
            vo.setNickname(u.getNickname());
            vo.setCreateTime(u.getCreateTime());
            list.add(vo);
        }
        return new PageData<>(list, (int) pg.getTotal());
    }

    private ParentConsentDocumentEntity findPublished() {
        return parentConsentDocumentDao.selectOne(
                new LambdaQueryWrapper<ParentConsentDocumentEntity>()
                        .eq(ParentConsentDocumentEntity::getStatus, ParentConsentDocumentEntity.STATUS_PUBLISHED)
                        .orderByDesc(ParentConsentDocumentEntity::getPublishedAt)
                        .last("LIMIT 1"));
    }

    private ParentConsentRecordEntity findAgreedRecord(Long parentUserId, String version) {
        if (parentUserId == null || StringUtils.isBlank(version)) {
            return null;
        }
        return parentConsentRecordDao.selectOne(
                new LambdaQueryWrapper<ParentConsentRecordEntity>()
                        .eq(ParentConsentRecordEntity::getParentUserId, parentUserId)
                        .eq(ParentConsentRecordEntity::getVersion, version));
    }

    private List<ParentDeviceBindingEntity> resolveDeviceBindings(String deviceId, String macAddress) {
        List<ParentDeviceBindingEntity> bindings = new ArrayList<>();
        if (StringUtils.isNotBlank(deviceId)) {
            bindings.addAll(ParentDeviceAccessHelper.findActiveBindingsForDevice(parentDeviceBindingDao, deviceId));
        }
        if (StringUtils.isNotBlank(macAddress)
                && !ParentDeviceAccessHelper.deviceIdsEquivalent(deviceId, macAddress)) {
            for (ParentDeviceBindingEntity b : ParentDeviceAccessHelper.findActiveBindingsForDevice(
                    parentDeviceBindingDao, macAddress)) {
                boolean dup = bindings.stream().anyMatch(x -> x.getId() != null && x.getId().equals(b.getId()));
                if (!dup) {
                    bindings.add(b);
                }
            }
        }
        return bindings;
    }

    private ParentDeviceBindingEntity findPrimaryOwnerForBindings(
            List<ParentDeviceBindingEntity> bindings, String deviceId, String macAddress) {
        ParentDeviceBindingEntity owner = null;
        if (StringUtils.isNotBlank(deviceId)) {
            owner = ParentDeviceAccessHelper.findPrimaryOwner(parentDeviceBindingDao, deviceId);
        }
        if (owner == null && StringUtils.isNotBlank(macAddress)) {
            owner = ParentDeviceAccessHelper.findPrimaryOwner(parentDeviceBindingDao, macAddress);
        }
        if (owner != null) {
            return owner;
        }
        return bindings.stream()
                .filter(ParentDeviceAccessHelper::isOwner)
                .findFirst()
                .orElse(null);
    }

    private String getDeviceBlockMode() {
        String v = sysParamsService.getValue(PARAM_DEVICE_BLOCK_MODE, true);
        v = StringUtils.trimToEmpty(v).toLowerCase(Locale.ROOT);
        return MODE_ALL_MEMBERS.equals(v) ? MODE_ALL_MEMBERS : MODE_OWNER_ONLY;
    }

    private int getRetentionDaysDisplay() {
        String v = sysParamsService.getValue(PARAM_RETENTION_DAYS, true);
        try {
            return Integer.parseInt(StringUtils.trimToEmpty(v));
        } catch (NumberFormatException e) {
            return 180;
        }
    }

    private int countParentUsers() {
        Long count = parentUserDao.selectCount(new LambdaQueryWrapper<>());
        return count != null ? count.intValue() : 0;
    }

    private int countAgreedForVersion(String version) {
        Long count = parentConsentRecordDao.selectCount(
                new LambdaQueryWrapper<ParentConsentRecordEntity>()
                        .eq(ParentConsentRecordEntity::getVersion, version));
        return count != null ? count.intValue() : 0;
    }

    private String nextVersionString() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = day + "_v";
        List<ParentConsentDocumentEntity> today = parentConsentDocumentDao.selectList(
                new LambdaQueryWrapper<ParentConsentDocumentEntity>()
                        .likeRight(ParentConsentDocumentEntity::getVersion, prefix));
        int max = 0;
        for (ParentConsentDocumentEntity e : today) {
            String ver = e.getVersion();
            if (ver == null || !ver.startsWith(prefix)) {
                continue;
            }
            try {
                max = Math.max(max, Integer.parseInt(ver.substring(prefix.length())));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return prefix + (max + 1);
    }

    private static ParentConsentDocumentVO toDocumentVo(ParentConsentDocumentEntity doc) {
        ParentConsentDocumentVO vo = new ParentConsentDocumentVO();
        vo.setVersion(doc.getVersion());
        vo.setTitle(doc.getTitle());
        vo.setSummary(doc.getSummary());
        vo.setContent(doc.getContent());
        vo.setPublishedAt(doc.getPublishedAt());
        return vo;
    }

    private static int parseInt(Map<String, Object> params, String key, int def) {
        if (params == null || params.get(key) == null) {
            return def;
        }
        try {
            return Integer.parseInt(params.get(key).toString());
        } catch (NumberFormatException e) {
            return def;
        }
    }
}
