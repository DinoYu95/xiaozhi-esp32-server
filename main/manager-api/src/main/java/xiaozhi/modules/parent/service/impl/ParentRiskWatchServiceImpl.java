package xiaozhi.modules.parent.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentRiskPreferenceDao;
import xiaozhi.modules.parent.dao.ParentRiskWatchDao;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.dto.ParentRiskPreferenceSaveDTO;
import xiaozhi.modules.parent.dto.ParentRiskWatchAuditDTO;
import xiaozhi.modules.parent.dto.ParentRiskWatchCreateDTO;
import xiaozhi.modules.parent.dto.ParentRiskWatchFromIntentDTO;
import xiaozhi.modules.parent.entity.ParentRiskPreferenceEntity;
import xiaozhi.modules.parent.entity.ParentRiskWatchEntity;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.service.ParentRiskWatchAssistService;
import xiaozhi.modules.parent.service.ParentRiskWatchService;
import xiaozhi.modules.parent.util.ParentChildAccessHelper;
import xiaozhi.modules.parent.vo.ParentRiskPreferenceVO;
import xiaozhi.modules.parent.vo.ParentRiskWatchDraftVO;
import xiaozhi.modules.parent.vo.ParentRiskWatchOverviewVO;
import xiaozhi.modules.parent.vo.ParentRiskWatchVO;
import xiaozhi.modules.risk.dao.ChildRiskRuleDao;
import xiaozhi.modules.risk.entity.ChildRiskRuleEntity;
import xiaozhi.modules.risk.service.ChildRiskService;
import xiaozhi.modules.risk.vo.ChildRiskDomainVO;

@Service
@RequiredArgsConstructor
public class ParentRiskWatchServiceImpl implements ParentRiskWatchService {

    private static final int MAX_EVALUATOR = 2;
    private static final int MAX_KEYWORD = 5;
    private static final Set<String> DOMAINS = Set.of(
            "psychological", "peer_relation", "family", "school", "online_safety", "physical_health", "other");

    private final ParentRiskWatchDao parentRiskWatchDao;
    private final ParentRiskPreferenceDao parentRiskPreferenceDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final DeviceChildDao deviceChildDao;
    private final ParentUserDao parentUserDao;
    private final ChildRiskRuleDao childRiskRuleDao;
    private final ChildRiskService childRiskService;
    private final ParentRiskWatchAssistService parentRiskWatchAssistService;

    @Override
    public ParentRiskWatchOverviewVO getOverview(Long parentUserId, Long childId) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
        ParentRiskWatchOverviewVO vo = new ParentRiskWatchOverviewVO();
        vo.setDomains(childRiskService.listRiskDomains());
        vo.setPreference(loadPreferenceVo(parentUserId, childId));
        vo.setMaxEvaluatorPerChild(MAX_EVALUATOR);
        vo.setMaxKeywordPerChild(MAX_KEYWORD);
        List<ParentRiskWatchEntity> rows = parentRiskWatchDao.selectList(
                new LambdaQueryWrapper<ParentRiskWatchEntity>()
                        .eq(ParentRiskWatchEntity::getParentUserId, parentUserId)
                        .eq(ParentRiskWatchEntity::getChildId, childId)
                        .orderByDesc(ParentRiskWatchEntity::getId));
        Map<String, String> domainNames = domainNameMap();
        vo.setMyWatches(rows.stream().map(e -> toVo(e, domainNames)).collect(Collectors.toList()));
        return vo;
    }

    @Override
    public ParentRiskPreferenceVO savePreference(Long parentUserId, ParentRiskPreferenceSaveDTO dto) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, dto.getChildId());
        List<String> domains = normalizeDomains(dto.getFocusDomains());
        ParentRiskPreferenceEntity row = parentRiskPreferenceDao.selectOne(
                new LambdaQueryWrapper<ParentRiskPreferenceEntity>()
                        .eq(ParentRiskPreferenceEntity::getParentUserId, parentUserId)
                        .eq(ParentRiskPreferenceEntity::getChildId, dto.getChildId()));
        Date now = new Date();
        if (row == null) {
            row = new ParentRiskPreferenceEntity();
            row.setParentUserId(parentUserId);
            row.setChildId(dto.getChildId());
            row.setFocusDomains(JsonUtils.toJsonString(domains));
            row.setCreateTime(now);
            row.setUpdateTime(now);
            parentRiskPreferenceDao.insert(row);
        } else {
            row.setFocusDomains(JsonUtils.toJsonString(domains));
            row.setUpdateTime(now);
            parentRiskPreferenceDao.updateById(row);
        }
        ParentRiskPreferenceVO vo = new ParentRiskPreferenceVO();
        vo.setChildId(dto.getChildId());
        vo.setFocusDomains(domains);
        return vo;
    }

    @Override
    public ParentRiskWatchDraftVO draftFromIntent(Long parentUserId, ParentRiskWatchFromIntentDTO dto) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, dto.getChildId());
        return parentRiskWatchAssistService.generateDraft(
                dto.getWatchType(), dto.getUserIntent(), dto.getRefinement(), dto.getPreviousDraft());
    }

    @Override
    public ParentRiskWatchVO create(Long parentUserId, ParentRiskWatchCreateDTO dto) {
        ParentChildAccessHelper.ensureParentCanAccessChildById(
                deviceChildDao, parentDeviceBindingDao, parentUserId, dto.getChildId());
        String wt = normalizeWatchType(dto.getWatchType());
        assertWatchLimit(parentUserId, dto.getChildId(), wt);
        ParentRiskWatchEntity e = new ParentRiskWatchEntity();
        e.setParentUserId(parentUserId);
        e.setChildId(dto.getChildId());
        e.setWatchType(wt);
        e.setRiskDomain(normalizeDomain(dto.getRiskDomain()));
        e.setName(dto.getName().trim());
        e.setDescription(StringUtils.trimToNull(dto.getDescription()));
        e.setTriggerHint(StringUtils.trimToNull(dto.getTriggerHint()));
        if (ParentRiskWatchEntity.TYPE_KEYWORD.equals(wt)) {
            e.setPattern(StringUtils.trimToEmpty(dto.getPattern()));
            if (StringUtils.isBlank(e.getPattern())) {
                throw new RenException("请填写观察关键词");
            }
            e.setRuleType(ChildRiskRuleEntity.TYPE_KEYWORD);
            e.setRiskLevel(dto.getRiskLevel() == null ? 2 : clampLevel(dto.getRiskLevel()));
            e.setCategory(StringUtils.defaultIfBlank(dto.getCategory(), "other"));
        } else {
            e.setInstructions(StringUtils.trimToEmpty(dto.getInstructions()));
            if (StringUtils.isBlank(e.getInstructions())) {
                throw new RenException("请填写观察规则说明");
            }
            e.setAllowedCategories(normalizeCategoriesJson(dto.getAllowedCategories(), e.getRiskDomain()));
        }
        e.setStatus(ParentRiskWatchEntity.STATUS_PENDING);
        e.setVersion(1);
        e.setSortOrder(0);
        Date now = new Date();
        e.setCreateTime(now);
        e.setUpdateTime(now);
        parentRiskWatchDao.insert(e);
        return toVo(e, domainNameMap());
    }

    @Override
    public ParentRiskWatchVO getDetail(Long parentUserId, Long id) {
        ParentRiskWatchEntity e = requireOwned(parentUserId, id);
        return toVo(e, domainNameMap());
    }

    @Override
    public void disable(Long parentUserId, Long id) {
        ParentRiskWatchEntity e = requireOwned(parentUserId, id);
        if (ParentRiskWatchEntity.STATUS_PENDING.equals(e.getStatus())) {
            parentRiskWatchDao.deleteById(id);
            return;
        }
        e.setStatus(ParentRiskWatchEntity.STATUS_DISABLED);
        e.setUpdateTime(new Date());
        parentRiskWatchDao.updateById(e);
        if (e.getLinkedRuleId() != null) {
            ChildRiskRuleEntity rule = childRiskRuleDao.selectById(e.getLinkedRuleId());
            if (rule != null) {
                rule.setStatus(0);
                rule.setUpdateTime(new Date());
                childRiskRuleDao.updateById(rule);
            }
        }
    }

    @Override
    public PageData<ParentRiskWatchVO> adminPage(String status, int page, int limit) {
        LambdaQueryWrapper<ParentRiskWatchEntity> q = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(status)) {
            q.eq(ParentRiskWatchEntity::getStatus, status.trim().toLowerCase(Locale.ROOT));
        }
        q.orderByDesc(ParentRiskWatchEntity::getId);
        Page<ParentRiskWatchEntity> pg = parentRiskWatchDao.selectPage(new Page<>(page, limit), q);
        Map<String, String> domainNames = domainNameMap();
        List<ParentRiskWatchVO> list =
                pg.getRecords().stream().map(e -> toVo(e, domainNames)).collect(Collectors.toList());
        return new PageData<>(list, pg.getTotal());
    }

    @Override
    public ParentRiskWatchVO adminGetDetail(Long id) {
        ParentRiskWatchEntity e = parentRiskWatchDao.selectById(id);
        if (e == null) {
            throw new RenException("观察项不存在");
        }
        return toVo(e, domainNameMap());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminAudit(Long id, ParentRiskWatchAuditDTO dto) {
        ParentRiskWatchEntity e = parentRiskWatchDao.selectById(id);
        if (e == null) {
            throw new RenException("观察项不存在");
        }
        if (!ParentRiskWatchEntity.STATUS_PENDING.equals(e.getStatus())) {
            throw new RenException("仅待审核状态可操作");
        }
        String action = StringUtils.trimToEmpty(dto.getAction()).toLowerCase(Locale.ROOT);
        Date now = new Date();
        if ("approve".equals(action)) {
            e.setStatus(ParentRiskWatchEntity.STATUS_ENABLED);
            e.setAuditNote(StringUtils.trimToNull(dto.getAuditNote()));
            e.setRejectReason(null);
            e.setUpdateTime(now);
            if (ParentRiskWatchEntity.TYPE_KEYWORD.equals(e.getWatchType())) {
                ChildRiskRuleEntity rule = new ChildRiskRuleEntity();
                rule.setName("[家长]" + e.getName());
                rule.setRuleType(ChildRiskRuleEntity.TYPE_KEYWORD);
                rule.setPattern(e.getPattern());
                rule.setRiskLevel(e.getRiskLevel());
                rule.setCategory(e.getCategory());
                rule.setRuleScope("PARENT");
                rule.setParentUserId(e.getParentUserId());
                rule.setChildId(e.getChildId());
                rule.setStatus(1);
                rule.setSortOrder(1000 + (e.getSortOrder() != null ? e.getSortOrder() : 0));
                rule.setCreateTime(now);
                rule.setUpdateTime(now);
                childRiskRuleDao.insert(rule);
                e.setLinkedRuleId(rule.getId());
            }
            parentRiskWatchDao.updateById(e);
            return;
        }
        if ("reject".equals(action)) {
            e.setStatus(ParentRiskWatchEntity.STATUS_REJECTED);
            e.setRejectReason(StringUtils.trimToNull(dto.getRejectReason()));
            e.setAuditNote(StringUtils.trimToNull(dto.getAuditNote()));
            e.setUpdateTime(now);
            parentRiskWatchDao.updateById(e);
            return;
        }
        throw new RenException("action 须为 approve 或 reject");
    }

    /** 供 ChildRiskService 合并智伴规则 */
    public List<ChildRiskRuleEntity> listEnabledParentRules(Long childId) {
        if (childId == null) {
            return List.of();
        }
        return childRiskRuleDao.selectList(
                new LambdaQueryWrapper<ChildRiskRuleEntity>()
                        .eq(ChildRiskRuleEntity::getStatus, 1)
                        .eq(ChildRiskRuleEntity::getRuleScope, "PARENT")
                        .eq(ChildRiskRuleEntity::getChildId, childId));
    }

    public List<ParentRiskWatchEntity> listEnabledEvaluatorWatches(Long childId) {
        if (childId == null) {
            return List.of();
        }
        return parentRiskWatchDao.selectList(
                new LambdaQueryWrapper<ParentRiskWatchEntity>()
                        .eq(ParentRiskWatchEntity::getChildId, childId)
                        .eq(ParentRiskWatchEntity::getWatchType, ParentRiskWatchEntity.TYPE_EVALUATOR)
                        .eq(ParentRiskWatchEntity::getStatus, ParentRiskWatchEntity.STATUS_ENABLED)
                        .orderByAsc(ParentRiskWatchEntity::getSortOrder)
                        .orderByAsc(ParentRiskWatchEntity::getId));
    }

    public ParentRiskPreferenceVO loadPreferenceForAgent(Long childId) {
        ParentRiskPreferenceEntity row = parentRiskPreferenceDao.selectOne(
                new LambdaQueryWrapper<ParentRiskPreferenceEntity>()
                        .eq(ParentRiskPreferenceEntity::getChildId, childId)
                        .last("LIMIT 1"));
        if (row == null) {
            ParentRiskPreferenceVO vo = new ParentRiskPreferenceVO();
            vo.setChildId(childId);
            vo.setFocusDomains(List.of());
            return vo;
        }
        ParentRiskPreferenceVO vo = new ParentRiskPreferenceVO();
        vo.setChildId(childId);
        vo.setFocusDomains(parseDomains(row.getFocusDomains()));
        return vo;
    }

    private ParentRiskPreferenceVO loadPreferenceVo(Long parentUserId, Long childId) {
        ParentRiskPreferenceEntity row = parentRiskPreferenceDao.selectOne(
                new LambdaQueryWrapper<ParentRiskPreferenceEntity>()
                        .eq(ParentRiskPreferenceEntity::getParentUserId, parentUserId)
                        .eq(ParentRiskPreferenceEntity::getChildId, childId));
        ParentRiskPreferenceVO vo = new ParentRiskPreferenceVO();
        vo.setChildId(childId);
        vo.setFocusDomains(row == null ? List.of() : parseDomains(row.getFocusDomains()));
        return vo;
    }

    private ParentRiskWatchEntity requireOwned(Long parentUserId, Long id) {
        ParentRiskWatchEntity e = parentRiskWatchDao.selectById(id);
        if (e == null || !parentUserId.equals(e.getParentUserId())) {
            throw new RenException(ErrorCode.PARENT_RISK_WATCH_NOT_FOUND);
        }
        return e;
    }

    private void assertWatchLimit(Long parentUserId, Long childId, String watchType) {
        long cnt = parentRiskWatchDao.selectCount(
                new LambdaQueryWrapper<ParentRiskWatchEntity>()
                        .eq(ParentRiskWatchEntity::getParentUserId, parentUserId)
                        .eq(ParentRiskWatchEntity::getChildId, childId)
                        .eq(ParentRiskWatchEntity::getWatchType, watchType)
                        .in(
                                ParentRiskWatchEntity::getStatus,
                                ParentRiskWatchEntity.STATUS_PENDING,
                                ParentRiskWatchEntity.STATUS_ENABLED));
        int max = ParentRiskWatchEntity.TYPE_EVALUATOR.equals(watchType) ? MAX_EVALUATOR : MAX_KEYWORD;
        if (cnt >= max) {
            throw new RenException("该类型观察已达上限 " + max + " 条");
        }
    }

    private static String normalizeWatchType(String wt) {
        String t = StringUtils.trimToEmpty(wt).toUpperCase(Locale.ROOT);
        if (!ParentRiskWatchEntity.TYPE_KEYWORD.equals(t) && !ParentRiskWatchEntity.TYPE_EVALUATOR.equals(t)) {
            throw new RenException("watchType 须为 KEYWORD 或 EVALUATOR");
        }
        return t;
    }

    private static String normalizeDomain(String d) {
        String x = StringUtils.trimToEmpty(d).toLowerCase(Locale.ROOT);
        if (!DOMAINS.contains(x)) {
            throw new RenException("无效的风险领域");
        }
        return x;
    }

    private static List<String> normalizeDomains(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String d : raw) {
            String x = StringUtils.trimToEmpty(d).toLowerCase(Locale.ROOT);
            if (DOMAINS.contains(x) && !out.contains(x)) {
                out.add(x);
            }
        }
        return out;
    }

    private static List<String> parseDomains(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            List<String> list = JsonUtils.parseArray(json, String.class);
            return list == null ? List.of() : list;
        } catch (Exception e) {
            return List.of();
        }
    }

    private String normalizeCategoriesJson(String raw, String domain) {
        if (StringUtils.isBlank(raw)) {
            return "[\"other\"]";
        }
        String t = raw.trim();
        if (!t.startsWith("[")) {
            t = "[\"" + t.replace("\"", "") + "\"]";
        }
        return t;
    }

    private static int clampLevel(Integer lvl) {
        if (lvl == null) {
            return 2;
        }
        return Math.max(1, Math.min(3, lvl));
    }

    private Map<String, String> domainNameMap() {
        Map<String, String> m = new LinkedHashMap<>();
        for (ChildRiskDomainVO d : childRiskService.listRiskDomains()) {
            m.put(d.getCode(), d.getName());
        }
        return m;
    }

    private ParentRiskWatchVO toVo(ParentRiskWatchEntity e, Map<String, String> domainNames) {
        ParentRiskWatchVO vo = new ParentRiskWatchVO();
        vo.setId(e.getId());
        vo.setParentUserId(e.getParentUserId());
        vo.setChildId(e.getChildId());
        vo.setWatchType(e.getWatchType());
        vo.setRiskDomain(e.getRiskDomain());
        vo.setRiskDomainName(domainNames.getOrDefault(e.getRiskDomain(), e.getRiskDomain()));
        vo.setName(e.getName());
        vo.setDescription(e.getDescription());
        vo.setTriggerHint(e.getTriggerHint());
        vo.setPattern(e.getPattern());
        vo.setRiskLevel(e.getRiskLevel());
        vo.setCategory(e.getCategory());
        vo.setInstructions(e.getInstructions());
        vo.setAllowedCategories(e.getAllowedCategories());
        vo.setStatus(e.getStatus());
        vo.setStatusLabel(statusLabel(e.getStatus()));
        vo.setRejectReason(e.getRejectReason());
        vo.setEditable(
                ParentRiskWatchEntity.STATUS_PENDING.equals(e.getStatus())
                        || ParentRiskWatchEntity.STATUS_ENABLED.equals(e.getStatus()));
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }

    private static String statusLabel(String st) {
        if (st == null) {
            return "";
        }
        return switch (st) {
            case ParentRiskWatchEntity.STATUS_PENDING -> "待审核";
            case ParentRiskWatchEntity.STATUS_ENABLED -> "已启用";
            case ParentRiskWatchEntity.STATUS_REJECTED -> "未通过";
            case ParentRiskWatchEntity.STATUS_DISABLED -> "已停用";
            default -> st;
        };
    }
}
