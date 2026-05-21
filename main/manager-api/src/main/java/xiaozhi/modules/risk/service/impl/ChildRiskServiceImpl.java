package xiaozhi.modules.risk.service.impl;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.risk.dao.ChildRiskEvaluatorDao;
import xiaozhi.modules.risk.dao.ChildRiskEventDao;
import xiaozhi.modules.risk.dao.ChildRiskOutboxDao;
import xiaozhi.modules.risk.dao.ChildRiskRuleDao;
import xiaozhi.modules.risk.dao.ParentRiskNotificationDao;
import xiaozhi.modules.risk.dto.ChildRiskConfigSaveDTO;
import xiaozhi.modules.risk.dto.ChildRiskEvaluatorSaveDTO;
import xiaozhi.modules.risk.dto.ChildRiskRuleSaveDTO;
import xiaozhi.modules.risk.dto.ChildRiskSignalDTO;
import xiaozhi.modules.risk.entity.ChildRiskEvaluatorEntity;
import xiaozhi.modules.risk.entity.ChildRiskEventEntity;
import xiaozhi.modules.risk.entity.ChildRiskOutboxEntity;
import xiaozhi.modules.risk.entity.ChildRiskRuleEntity;
import xiaozhi.modules.risk.entity.ParentRiskNotificationEntity;
import xiaozhi.modules.risk.service.ChildRiskService;
import xiaozhi.modules.risk.vo.ChildRiskAgentRuntimeVO;
import xiaozhi.modules.risk.vo.ChildRiskConfigVO;
import xiaozhi.modules.risk.vo.ChildRiskDomainVO;
import xiaozhi.modules.risk.vo.ChildRiskEvaluatorPublicVO;
import xiaozhi.modules.risk.vo.ChildRiskEventAdminVO;
import xiaozhi.modules.risk.vo.ChildRiskRulePublicVO;
import xiaozhi.modules.risk.vo.ChildRiskSignalResultVO;
import xiaozhi.modules.risk.vo.ParentRiskNotificationDetailVO;
import xiaozhi.modules.risk.vo.ParentRiskNotificationPageVO;
import xiaozhi.modules.risk.vo.ParentRiskNotificationVO;
import xiaozhi.modules.sys.service.SysParamsService;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChildRiskServiceImpl implements ChildRiskService {

    private static final String PARAM_KEY = "server.child_risk_config";

    private final SysParamsService sysParamsService;
    private final DeviceChildDao deviceChildDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final ChildRiskRuleDao childRiskRuleDao;
    private final ChildRiskEvaluatorDao childRiskEvaluatorDao;
    private final ChildRiskEventDao childRiskEventDao;
    private final ChildRiskOutboxDao childRiskOutboxDao;
    private final ParentRiskNotificationDao parentRiskNotificationDao;

    @Data
    private static final class RiskCfg {
        private boolean enabled;
        private int cooldownMinutes = 30;
        /** 上报的 risk_level 小于等于该值则通知（1 最严重；设为 3 即 1~3 皆可） */
        private int notifyIfRiskLevelLte = 3;
        private int evalEveryNRounds = 3;
        private String judgmentMode = "HYBRID";
        private boolean routerEnabled = true;
        private int maxDomainsPerRound = 2;
        private double minConfidenceToAlert = 0.65;
    }

    private RiskCfg loadCfg() {
        String json = sysParamsService.getValue(PARAM_KEY, true);
        RiskCfg c = new RiskCfg();
        if (StringUtils.isBlank(json)) {
            return c;
        }
        try {
            RiskCfg parsed = JsonUtils.parseObject(json, RiskCfg.class);
            if (parsed != null) {
                return parsed;
            }
        } catch (Exception e) {
            log.warn("解析 child_risk_config 失败: {}", e.getMessage());
        }
        return c;
    }

    @Override
    public void verifyParentOwnsChild(Long parentUserId, Long childId) {
        DeviceChildEntity ch = deviceChildDao.selectById(childId);
        if (ch == null || StringUtils.isBlank(ch.getDeviceId())) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "孩子不存在或未绑定设备");
        }
        String dev = ch.getDeviceId();
        ParentDeviceBindingEntity b =
                parentDeviceBindingDao.selectOne(new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, dev)
                                .or()
                                .eq(ParentDeviceBindingEntity::getDeviceId, normalizeDev(dev)))
                        .last("LIMIT 1"));
        if (b == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
    }

    @Override
    public ChildRiskSignalResultVO receiveSignal(ChildRiskSignalDTO dto) {
        RiskCfg cfg = loadCfg();
        Long ingressChild =
                dto == null ? null : dto.getChildId();
        String ingressDev =
                dto == null ? null : dto.getDeviceId();
        Integer ingressLevel = dto == null ? null : dto.getRiskLevel();
        Boolean ingressNeed = dto == null ? null : dto.getNeedAlert();
        String ingressCat = dto == null ? null : dto.getCategory();
        String ingressSrc = dto == null ? null : dto.getSource();

        log.info(
                "[child_risk signal] ingress childId={} deviceId={} riskLevel={} needAlert={} category={} source={}; "
                        + "cfg(enabled={}, notifyIfRiskLevelLte={}, cooldownMinutes={}, evalEveryNRounds={})",
                ingressChild,
                ingressDev,
                ingressLevel,
                ingressNeed,
                ingressCat,
                ingressSrc,
                cfg.isEnabled(),
                cfg.getNotifyIfRiskLevelLte(),
                cfg.getCooldownMinutes(),
                cfg.getEvalEveryNRounds());

        if (!cfg.enabled) {
            log.info("[child_risk signal] outcome=DISABLED (global switch off)，不写 event/outbox");
            return new ChildRiskSignalResultVO(null, true, "DISABLED");
        }
        if (dto == null || dto.getChildId() == null || StringUtils.isBlank(dto.getDeviceId())) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "childId、deviceId 必填");
        }
        if (dto.getNeedAlert() == null || !Boolean.TRUE.equals(dto.getNeedAlert())) {
            log.info("[child_risk signal] outcome=NO_ALERT_FLAG childId={} needAlert=false，不写 event/outbox", dto.getChildId());
            return new ChildRiskSignalResultVO(null, true, "NO_ALERT_FLAG");
        }
        int level = dto.getRiskLevel() == null ? 3 : Math.max(1, Math.min(3, dto.getRiskLevel()));
        String category = StringUtils.defaultIfBlank(dto.getCategory(), "other");
        String source = normalizeSource(StringUtils.defaultIfBlank(dto.getSource(), "ZhibAN_JSON"));

        DeviceChildEntity child = deviceChildDao.selectById(dto.getChildId());
        if (child == null) {
            throw new RenException("孩子不存在");
        }
        String dev = StringUtils.trimToEmpty(child.getDeviceId());
        String reqDev = StringUtils.trimToEmpty(dto.getDeviceId());
        if (!dev.equals(reqDev) && !normalizeDev(dev).equals(normalizeDev(reqDev))) {
            throw new RenException("deviceId 与孩子绑定不一致");
        }

        if (level > cfg.notifyIfRiskLevelLte) {
            log.info(
                    "[child_risk signal] branch=LEVEL_FILTER level={} gt notifyIfRiskLevelLte={} → 写入 SUPPRESSED 行",
                    level,
                    cfg.getNotifyIfRiskLevelLte());
            return insertSuppressed(child, dto, level, category, source, "LEVEL_FILTER");
        }

        long cooldownMs = Math.max(1, cfg.cooldownMinutes) * 60_000L;
        Date since = new Date(System.currentTimeMillis() - cooldownMs);
        ChildRiskEventEntity last = childRiskEventDao.selectOne(
                new LambdaQueryWrapper<ChildRiskEventEntity>()
                        .eq(ChildRiskEventEntity::getChildId, dto.getChildId())
                        .eq(ChildRiskEventEntity::getCategory, category)
                        .ge(ChildRiskEventEntity::getCreateTime, since)
                        .in(
                                ChildRiskEventEntity::getStatus,
                                List.of("WAIT_NOTIFY", "DONE"))
                        .orderByDesc(ChildRiskEventEntity::getCreateTime)
                        .last("LIMIT 1"));
        if (last != null) {
            log.info(
                    "[child_risk signal] branch=COOLDOWN childId={} category={} lastEventId={} → 写入 SUPPRESSED",
                    dto.getChildId(),
                    category,
                    last.getId());
            return insertSuppressed(child, dto, level, category, source, "COOLDOWN");
        }

        firstParentUserId(dev);
        ChildRiskEventEntity ev = new ChildRiskEventEntity();
        ev.setDeviceId(dev);
        ev.setChildId(dto.getChildId());
        ev.setParentUserId(firstParentUserId(dev));
        ev.setSessionId(dto.getSessionId());
        ev.setRiskLevel(level);
        ev.setCategory(category);
        ev.setSource(source);
        ev.setReasonPublic(StringUtils.trimToEmpty(dto.getReasonPublic()));
        ev.setStatus("WAIT_NOTIFY");
        ev.setSuppressedReason(null);
        ev.setCreateTime(new Date());
        childRiskEventDao.insert(ev);

        ChildRiskOutboxEntity ob = new ChildRiskOutboxEntity();
        ob.setEventId(ev.getId());
        ob.setChannel("MINI_APP");
        ob.setStatus(ChildRiskOutboxEntity.ST_PENDING);
        ob.setAttempts(0);
        ob.setNextRetryTime(null);
        ob.setCreateTime(new Date());
        ob.setUpdateTime(new Date());
        childRiskOutboxDao.insert(ob);

        log.info(
                "[child_risk signal] outcome=WAIT_NOTIFY eventId={} outboxId={} childId={} category={} level={} status=SUPPRESSED?=false → 待发小程序通知",
                ev.getId(),
                ob.getId(),
                dto.getChildId(),
                category,
                level);
        return new ChildRiskSignalResultVO(ev.getId(), false, null);
    }

    @Override
    public void processPendingOutboxBatch() {
        List<ChildRiskOutboxEntity> list =
                childRiskOutboxDao.selectList(new LambdaQueryWrapper<ChildRiskOutboxEntity>()
                        .eq(ChildRiskOutboxEntity::getStatus, ChildRiskOutboxEntity.ST_PENDING)
                        .and(w -> w.isNull(ChildRiskOutboxEntity::getNextRetryTime).or()
                                .le(ChildRiskOutboxEntity::getNextRetryTime, new Date()))
                        .last("LIMIT 30"));
        for (ChildRiskOutboxEntity ob : list) {
            try {
                flushOutboxItem(ob);
            } catch (Exception e) {
                log.warn("flush outbox id={}: {}", ob.getId(), e.getMessage(), e);
                int att = ob.getAttempts() == null ? 0 : ob.getAttempts();
                ChildRiskOutboxEntity up = new ChildRiskOutboxEntity();
                up.setId(ob.getId());
                up.setAttempts(att + 1);
                up.setFailMessage(StringUtils.abbreviate(e.getMessage(), 500));
                if (att >= 10) {
                    up.setStatus(ChildRiskOutboxEntity.ST_FAILED);
                    childRiskOutboxDao.updateById(up);
                } else {
                    long backoff = Math.min(300_000L, (long) Math.pow(2, att + 3) * 1000);
                    up.setNextRetryTime(new Date(System.currentTimeMillis() + backoff));
                    childRiskOutboxDao.updateById(up);
                }
            }
        }
    }

    private ChildRiskSignalResultVO insertSuppressed(
            DeviceChildEntity child,
            ChildRiskSignalDTO dto,
            int level,
            String category,
            String source,
            String reason) {
        ChildRiskEventEntity ev = new ChildRiskEventEntity();
        ev.setDeviceId(child.getDeviceId());
        ev.setChildId(dto.getChildId());
        ev.setParentUserId(firstParentUserId(child.getDeviceId()));
        ev.setSessionId(dto.getSessionId());
        ev.setRiskLevel(level);
        ev.setCategory(category);
        ev.setSource(source);
        ev.setReasonPublic(StringUtils.trimToEmpty(dto.getReasonPublic()));
        ev.setStatus("SUPPRESSED");
        ev.setSuppressedReason(reason);
        ev.setCreateTime(new Date());
        childRiskEventDao.insert(ev);
        log.info(
                "[child_risk signal] outcome={} persisted eventId={} status=SUPPRESSED childId={} category={} level={}",
                reason,
                ev.getId(),
                dto.getChildId(),
                category,
                level);
        return new ChildRiskSignalResultVO(ev.getId(), true, reason);
    }

    private Long firstParentUserId(String deviceId) {
        if (StringUtils.isBlank(deviceId)) {
            return null;
        }
        String normalized = normalizeDev(deviceId);
        ParentDeviceBindingEntity one = parentDeviceBindingDao.selectOne(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                        .or()
                        .eq(ParentDeviceBindingEntity::getDeviceId, normalized)
                        .last("LIMIT 1"));
        return one == null ? null : one.getParentUserId();
    }

    private static String normalizeDev(String deviceId) {
        return deviceId.replace(":", "_").toLowerCase();
    }

    /** SKILL:{domain}:{code} 等来源可能超过旧表 VARCHAR(32)，入库前统一截断到 128 */
    private String normalizeSource(String source) {
        String s = StringUtils.trimToEmpty(source);
        if (s.length() <= 128) {
            return s;
        }
        log.warn("[child_risk signal] source truncated from {} to 128 chars: {}", s.length(), StringUtils.abbreviate(s, 80));
        return s.substring(0, 128);
    }

    @Override
    public List<ChildRiskRulePublicVO> listEnabledRulesForAgent() {
        List<ChildRiskRuleEntity> rows = childRiskRuleDao.selectList(
                new LambdaQueryWrapper<ChildRiskRuleEntity>()
                        .eq(ChildRiskRuleEntity::getStatus, 1)
                        .orderByAsc(ChildRiskRuleEntity::getSortOrder)
                        .orderByAsc(ChildRiskRuleEntity::getId));
        return rows.stream().map(this::toRulePub).collect(Collectors.toList());
    }

    @Override
    public ChildRiskAgentRuntimeVO getAgentRiskRuntime() {
        RiskCfg c = loadCfg();
        ChildRiskAgentRuntimeVO v = new ChildRiskAgentRuntimeVO();
        v.setEnabled(c.isEnabled());
        int ev = c.getEvalEveryNRounds();
        v.setEvalEveryNRounds(Math.max(1, Math.min(99, ev <= 0 ? 3 : ev)));
        v.setJudgmentMode(StringUtils.defaultIfBlank(c.getJudgmentMode(), "HYBRID"));
        v.setRouterEnabled(c.isRouterEnabled());
        int md = c.getMaxDomainsPerRound();
        v.setMaxDomainsPerRound(Math.max(1, Math.min(3, md <= 0 ? 2 : md)));
        double mc = c.getMinConfidenceToAlert();
        v.setMinConfidenceToAlert(mc <= 0 ? 0.65 : Math.min(1.0, mc));
        return v;
    }

    @Override
    public ChildRiskConfigVO getAdminChildRiskConfig() {
        RiskCfg c = loadCfg();
        ChildRiskConfigVO v = new ChildRiskConfigVO();
        v.setEnabled(c.isEnabled());
        v.setCooldownMinutes(c.getCooldownMinutes());
        v.setNotifyIfRiskLevelLte(c.getNotifyIfRiskLevelLte());
        v.setEvalEveryNRounds(c.getEvalEveryNRounds());
        v.setJudgmentMode(StringUtils.defaultIfBlank(c.getJudgmentMode(), "HYBRID"));
        v.setRouterEnabled(c.isRouterEnabled());
        v.setMaxDomainsPerRound(c.getMaxDomainsPerRound());
        v.setMinConfidenceToAlert(c.getMinConfidenceToAlert());
        return v;
    }

    @Override
    public void saveAdminChildRiskConfig(ChildRiskConfigSaveDTO dto) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("enabled", Boolean.TRUE.equals(dto.getEnabled()));
        int cd = dto.getCooldownMinutes() == null ? 30 : Math.max(1, Math.min(10080, dto.getCooldownMinutes()));
        int nfl = dto.getNotifyIfRiskLevelLte() == null ? 3 : Math.max(1, Math.min(3, dto.getNotifyIfRiskLevelLte()));
        int ev = dto.getEvalEveryNRounds() == null ? 3 : Math.max(1, Math.min(99, dto.getEvalEveryNRounds()));
        m.put("cooldownMinutes", cd);
        m.put("notifyIfRiskLevelLte", nfl);
        m.put("evalEveryNRounds", ev);
        String jm = StringUtils.trimToEmpty(dto.getJudgmentMode());
        m.put("judgmentMode", jm.isEmpty() ? "HYBRID" : jm.toUpperCase());
        m.put("routerEnabled", dto.getRouterEnabled() == null || Boolean.TRUE.equals(dto.getRouterEnabled()));
        int md = dto.getMaxDomainsPerRound() == null ? 2 : Math.max(1, Math.min(3, dto.getMaxDomainsPerRound()));
        m.put("maxDomainsPerRound", md);
        double mc = dto.getMinConfidenceToAlert() == null ? 0.65 : Math.max(0, Math.min(1, dto.getMinConfidenceToAlert()));
        m.put("minConfidenceToAlert", mc);
        String json = JsonUtils.toJsonString(m);
        int n = sysParamsService.updateValueByCode(PARAM_KEY, json);
        if (n <= 0) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "未找到参数 server.child_risk_config，请确认已执行数据库迁移");
        }
        log.info("[child_risk config] admin saved {}", json);
    }

    private ChildRiskRulePublicVO toRulePub(ChildRiskRuleEntity e) {
        ChildRiskRulePublicVO v = new ChildRiskRulePublicVO();
        v.setId(e.getId());
        v.setName(e.getName());
        v.setRuleType(e.getRuleType());
        v.setPattern(e.getPattern());
        v.setRiskLevel(e.getRiskLevel());
        v.setCategory(e.getCategory());
        v.setStatus(e.getStatus());
        v.setSortOrder(e.getSortOrder());
        return v;
    }

    @Override
    public PageData<ChildRiskEventAdminVO> pageEvents(int page, int limit) {
        int p = Math.max(1, page);
        int size = limit <= 0 ? 20 : Math.min(limit, 100);
        Page<ChildRiskEventEntity> pg =
                childRiskEventDao.selectPage(new Page<>(p, size), new LambdaQueryWrapper<ChildRiskEventEntity>()
                        .orderByDesc(ChildRiskEventEntity::getCreateTime));
        List<ChildRiskEventAdminVO> list = pg.getRecords().stream().map(this::toAdminEv).collect(Collectors.toList());
        return new PageData<>(list, pg.getTotal());
    }

    private ChildRiskEventAdminVO toAdminEv(ChildRiskEventEntity e) {
        ChildRiskEventAdminVO v = new ChildRiskEventAdminVO();
        v.setId(e.getId());
        v.setDeviceId(e.getDeviceId());
        v.setChildId(e.getChildId());
        v.setSessionId(e.getSessionId());
        v.setRiskLevel(e.getRiskLevel());
        v.setCategory(e.getCategory());
        v.setSource(e.getSource());
        v.setReasonPublic(e.getReasonPublic());
        v.setStatus(e.getStatus());
        v.setSuppressedReason(e.getSuppressedReason());
        v.setCreateTime(e.getCreateTime());
        return v;
    }

    @Override
    public void saveOrUpdateRule(ChildRiskRuleSaveDTO dto) {
        Date now = new Date();
        if (dto.getId() == null) {
            ChildRiskRuleEntity e = new ChildRiskRuleEntity();
            fillRuleEntity(dto, e);
            e.setCreateTime(now);
            e.setUpdateTime(now);
            childRiskRuleDao.insert(e);
            return;
        }
        ChildRiskRuleEntity old = childRiskRuleDao.selectById(dto.getId());
        if (old == null) {
            throw new RenException("规则不存在");
        }
        fillRuleEntity(dto, old);
        old.setUpdateTime(now);
        childRiskRuleDao.updateById(old);
    }

    private static void fillRuleEntity(ChildRiskRuleSaveDTO dto, ChildRiskRuleEntity e) {
        e.setName(dto.getName().trim());
        e.setRuleType(dto.getRuleType().trim().toUpperCase());
        e.setPattern(dto.getPattern());
        e.setRiskLevel(dto.getRiskLevel());
        e.setCategory(dto.getCategory().trim());
        e.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
        e.setStatus(dto.getStatus());
    }

    @Override
    public void deleteRule(Long id) {
        childRiskRuleDao.deleteById(id);
    }

    @Override
    public List<ChildRiskRulePublicVO> listAllRulesForAdmin() {
        return childRiskRuleDao
                .selectList(new LambdaQueryWrapper<ChildRiskRuleEntity>()
                        .orderByAsc(ChildRiskRuleEntity::getSortOrder)
                        .orderByAsc(ChildRiskRuleEntity::getId))
                .stream()
                .map(this::toRulePub)
                .collect(Collectors.toList());
    }

    @Override
    public List<ChildRiskEvaluatorPublicVO> listEnabledEvaluatorsForAgent() {
        return childRiskEvaluatorDao
                .selectList(new LambdaQueryWrapper<ChildRiskEvaluatorEntity>()
                        .eq(ChildRiskEvaluatorEntity::getStatus, 1)
                        .orderByAsc(ChildRiskEvaluatorEntity::getSortOrder)
                        .orderByAsc(ChildRiskEvaluatorEntity::getId))
                .stream()
                .map(this::toEvaluatorPub)
                .collect(Collectors.toList());
    }

    @Override
    public List<ChildRiskEvaluatorPublicVO> listAllEvaluatorsForAdmin() {
        return childRiskEvaluatorDao
                .selectList(new LambdaQueryWrapper<ChildRiskEvaluatorEntity>()
                        .orderByDesc(ChildRiskEvaluatorEntity::getCreateTime)
                        .orderByDesc(ChildRiskEvaluatorEntity::getId))
                .stream()
                .map(this::toEvaluatorPub)
                .collect(Collectors.toList());
    }

    @Override
    public void saveOrUpdateEvaluator(ChildRiskEvaluatorSaveDTO dto) {
        Date now = new Date();
        if (dto.getId() == null) {
            Long dup = childRiskEvaluatorDao.selectCount(new LambdaQueryWrapper<ChildRiskEvaluatorEntity>()
                    .eq(ChildRiskEvaluatorEntity::getCode, dto.getCode().trim()));
            if (dup != null && dup > 0) {
                throw new RenException("code 已存在");
            }
            ChildRiskEvaluatorEntity e = new ChildRiskEvaluatorEntity();
            fillEvaluatorEntity(dto, e);
            e.setCreateTime(now);
            e.setUpdateTime(now);
            childRiskEvaluatorDao.insert(e);
            return;
        }
        ChildRiskEvaluatorEntity old = childRiskEvaluatorDao.selectById(dto.getId());
        if (old == null) {
            throw new RenException("判别器不存在");
        }
        fillEvaluatorEntity(dto, old);
        old.setUpdateTime(now);
        childRiskEvaluatorDao.updateById(old);
    }

    @Override
    public void deleteEvaluator(Long id) {
        childRiskEvaluatorDao.deleteById(id);
    }

    @Override
    public List<ChildRiskDomainVO> listRiskDomains() {
        List<ChildRiskDomainVO> list = new ArrayList<>();
        list.add(domain("psychological", "心理情绪",
                Arrays.asList("emotion_distress", "self_harm_hint", "hopelessness", "anxiety_severe", "other")));
        list.add(domain("peer_relation", "同伴关系",
                Arrays.asList("social_exclusion", "bullying", "peer_conflict", "loneliness_school", "other")));
        list.add(domain("family", "家庭关系",
                Arrays.asList("family_conflict", "neglect_hint", "abuse_hint", "other")));
        list.add(domain("school", "学业校园",
                Arrays.asList("school_stress", "academic_burnout", "school_refusal", "other")));
        list.add(domain("online_safety", "网络安全",
                Arrays.asList("grooming_hint", "privacy_leak", "cyberbullying", "inappropriate_content", "other")));
        list.add(domain("physical_health", "身心健康",
                Arrays.asList("eating_disorder_hint", "substance_hint", "sleep_severe", "other")));
        return list;
    }

    private static ChildRiskDomainVO domain(String code, String name, List<String> cats) {
        ChildRiskDomainVO v = new ChildRiskDomainVO();
        v.setCode(code);
        v.setName(name);
        v.setSuggestedCategories(cats);
        return v;
    }

    private ChildRiskEvaluatorPublicVO toEvaluatorPub(ChildRiskEvaluatorEntity e) {
        ChildRiskEvaluatorPublicVO v = new ChildRiskEvaluatorPublicVO();
        v.setId(e.getId());
        v.setCode(e.getCode());
        v.setName(e.getName());
        v.setRiskDomain(e.getRiskDomain());
        v.setVersion(e.getVersion());
        v.setStatus(e.getStatus());
        v.setModelName(e.getModelName());
        v.setTemperature(e.getTemperature());
        v.setTimeoutMs(e.getTimeoutMs());
        v.setInstructions(e.getInstructions());
        v.setAllowedCategories(e.getAllowedCategories());
        v.setSortOrder(e.getSortOrder());
        return v;
    }

    private static void fillEvaluatorEntity(ChildRiskEvaluatorSaveDTO dto, ChildRiskEvaluatorEntity e) {
        e.setCode(dto.getCode().trim());
        e.setName(dto.getName().trim());
        e.setRiskDomain(dto.getRiskDomain().trim());
        e.setVersion(dto.getVersion() == null ? 1 : dto.getVersion());
        e.setStatus(dto.getStatus());
        e.setModelName(StringUtils.trimToNull(dto.getModelName()));
        e.setTemperature(dto.getTemperature() == null ? BigDecimal.ZERO : dto.getTemperature());
        e.setTimeoutMs(dto.getTimeoutMs() == null ? 45000 : dto.getTimeoutMs());
        e.setInstructions(dto.getInstructions());
        e.setAllowedCategories(dto.getAllowedCategories().trim());
        e.setSortOrder(dto.getSortOrder() == null ? 0 : dto.getSortOrder());
    }

    @Override
    public ParentRiskNotificationPageVO pageNotificationsForParent(
            Long parentUserId, Long childId, int page, int pageSize) {
        int p = Math.max(1, page);
        int sz = pageSize <= 0 ? 20 : Math.min(pageSize, 100);
        Page<ParentRiskNotificationEntity> pg =
                parentRiskNotificationDao.selectPage(new Page<>(p, sz),
                        new LambdaQueryWrapper<ParentRiskNotificationEntity>()
                                .eq(ParentRiskNotificationEntity::getParentUserId, parentUserId)
                                .eq(ParentRiskNotificationEntity::getChildId, childId)
                                .orderByDesc(ParentRiskNotificationEntity::getCreateTime));
        List<ParentRiskNotificationVO> list =
                pg.getRecords().stream().map(this::toNotifVo).collect(Collectors.toList());
        ParentRiskNotificationPageVO vo = new ParentRiskNotificationPageVO();
        vo.setList(list);
        vo.setTotal(pg.getTotal());
        vo.setPage(p);
        vo.setPageSize(sz);
        vo.setHasMore((long) p * sz < pg.getTotal());
        return vo;
    }

    private ParentRiskNotificationVO toNotifVo(ParentRiskNotificationEntity e) {
        ParentRiskNotificationVO v = new ParentRiskNotificationVO();
        v.setId(e.getId());
        v.setChildId(e.getChildId());
        v.setEventId(e.getEventId());
        v.setTitle(e.getTitle());
        v.setSummary(e.getSummary());
        v.setRiskLevel(e.getRiskLevel());
        v.setIsRead(e.getIsRead());
        v.setCreateTime(e.getCreateTime());
        return v;
    }

    @Override
    public long countUnreadForParent(Long parentUserId, Long childId) {
        return parentRiskNotificationDao.selectCount(new LambdaQueryWrapper<ParentRiskNotificationEntity>()
                .eq(ParentRiskNotificationEntity::getParentUserId, parentUserId)
                .eq(childId != null, ParentRiskNotificationEntity::getChildId, childId)
                .eq(ParentRiskNotificationEntity::getIsRead, 0));
    }

    @Override
    public void markReadForParent(Long parentUserId, Long notificationId) {
        ParentRiskNotificationEntity n =
                parentRiskNotificationDao.selectById(notificationId);
        if (n == null || !parentUserId.equals(n.getParentUserId())) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "通知不存在或无权限");
        }
        ParentRiskNotificationEntity p = new ParentRiskNotificationEntity();
        p.setId(n.getId());
        p.setIsRead(1);
        parentRiskNotificationDao.updateById(p);
    }

    @Override
    public ParentRiskNotificationDetailVO getRiskNotificationDetail(
            Long parentUserId, Long notificationId) {
        ParentRiskNotificationEntity n = parentRiskNotificationDao.selectById(notificationId);
        if (n == null || !parentUserId.equals(n.getParentUserId())) {
            throw new RenException(ErrorCode.PARAMS_GET_ERROR, "通知不存在或无权限");
        }
        verifyParentOwnsChild(parentUserId, n.getChildId());

        ParentRiskNotificationDetailVO vo = new ParentRiskNotificationDetailVO();
        vo.setId(n.getId());
        vo.setChildId(n.getChildId());
        vo.setEventId(n.getEventId());
        vo.setTitle(n.getTitle());
        vo.setSummary(n.getSummary());
        vo.setRiskLevel(n.getRiskLevel());
        vo.setIsRead(n.getIsRead());
        vo.setCreateTime(n.getCreateTime());

        if (n.getEventId() != null) {
            ChildRiskEventEntity ev = childRiskEventDao.selectById(n.getEventId());
            if (ev != null) {
                vo.setCategory(ev.getCategory());
                vo.setReasonPublic(ev.getReasonPublic());
                vo.setSessionId(ev.getSessionId());
                vo.setSource(ev.getSource());
                vo.setEventStatus(ev.getStatus());
                vo.setEventCreateTime(ev.getCreateTime());
            }
        }
        return vo;
    }

    /** outbox worker：成功后写「家长小程序」通知列表。 */
    void flushOutboxItem(ChildRiskOutboxEntity ob) throws Exception {
        ChildRiskEventEntity ev = childRiskEventDao.selectById(ob.getEventId());
        if (ev == null) {
            throw new RenException("事件不存在 eventId=" + ob.getEventId());
        }
        if (!ChildRiskOutboxEntity.ST_PENDING.equals(ob.getStatus())) {
            return;
        }
        List<ParentDeviceBindingEntity> binds =
                parentDeviceBindingDao.selectList(new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, ev.getDeviceId())
                                .or()
                                .eq(ParentDeviceBindingEntity::getDeviceId, normalizeDev(ev.getDeviceId()))));
        if (binds.isEmpty()) {
            ChildRiskOutboxEntity op = new ChildRiskOutboxEntity();
            op.setId(ob.getId());
            op.setStatus(ChildRiskOutboxEntity.ST_FAILED);
            op.setFailMessage("无家长绑定设备");
            op.setUpdateTime(new Date());
            childRiskOutboxDao.updateById(op);
            return;
        }
        String title =
                switch (ev.getRiskLevel()) {
                    case 1 -> "【高风险】孩子对话需要您关注";
                    case 2 -> "【请关注】孩子成长对话提示";
                    default -> "孩子对话提示";
                };
        Date now = new Date();
        for (ParentDeviceBindingEntity b : binds) {
            ParentRiskNotificationEntity n = new ParentRiskNotificationEntity();
            n.setParentUserId(b.getParentUserId());
            n.setChildId(ev.getChildId());
            n.setEventId(ev.getId());
            n.setTitle(title);
            n.setSummary(StringUtils.abbreviate(StringUtils.defaultString(ev.getReasonPublic()), 500));
            n.setRiskLevel(ev.getRiskLevel());
            n.setIsRead(0);
            n.setCreateTime(now);
            parentRiskNotificationDao.insert(n);
        }
        ChildRiskEventEntity patch = new ChildRiskEventEntity();
        patch.setId(ev.getId());
        patch.setStatus("DONE");
        childRiskEventDao.updateById(patch);

        ChildRiskOutboxEntity op = new ChildRiskOutboxEntity();
        op.setId(ob.getId());
        op.setStatus(ChildRiskOutboxEntity.ST_SUCCESS);
        op.setUpdateTime(now);
        childRiskOutboxDao.updateById(op);
    }
}
