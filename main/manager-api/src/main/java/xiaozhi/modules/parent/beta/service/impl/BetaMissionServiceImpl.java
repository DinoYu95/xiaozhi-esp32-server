package xiaozhi.modules.parent.beta.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.modules.parent.beta.BetaMissionStepRegistry;
import xiaozhi.modules.parent.beta.BetaMissionStepRegistry.StepDef;
import xiaozhi.modules.parent.beta.BetaMissionVerifyService;
import xiaozhi.modules.parent.beta.dao.BetaMissionUserStateDao;
import xiaozhi.modules.parent.beta.dto.BetaMissionAdminConfigSaveDTO;
import xiaozhi.modules.parent.beta.dto.BetaMissionContextDTO;
import xiaozhi.modules.parent.beta.entity.BetaMissionUserStateEntity;
import xiaozhi.modules.parent.beta.service.BetaMissionService;
import xiaozhi.modules.parent.beta.vo.BetaMissionAdminConfigVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionEntryStatusVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionFunnelStepVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionFunnelVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionOverviewVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionSectionVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionStepVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionUserDetailVO;
import xiaozhi.modules.parent.beta.vo.BetaMissionUserProgressVO;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.util.ParentBetaAccessHelper;
import xiaozhi.modules.parent.util.ParentChildAccessHelper;
import xiaozhi.modules.sys.service.SysParamsService;

@Service
@RequiredArgsConstructor
public class BetaMissionServiceImpl implements BetaMissionService {

    private static final String PARAM_BETA_MISSION_ENABLED = "server.beta_mission_enabled";

    private final BetaMissionUserStateDao betaMissionUserStateDao;
    private final ParentUserDao parentUserDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final DeviceChildDao deviceChildDao;
    private final BetaMissionVerifyService betaMissionVerifyService;
    private final SysParamsService sysParamsService;
    private final ObjectMapper objectMapper;

    @Override
    public BetaMissionEntryStatusVO getEntryStatus(Long parentUserId) {
        boolean global = isGlobalBetaMissionEnabled();
        boolean beta = hasBetaAccess(parentUserId);
        boolean viaSharing = ParentBetaAccessHelper.hasBetaAccessViaSharing(
                parentUserDao, parentDeviceBindingDao, parentUserId);
        BetaMissionEntryStatusVO vo = new BetaMissionEntryStatusVO();
        vo.setBetaMissionEnabled(global);
        vo.setBetaTester(beta);
        vo.setBetaAccessViaSharing(viaSharing);
        vo.setShowEntry(global && beta);
        vo.setRequiredTotal(BetaMissionStepRegistry.requiredCount());
        if (global && beta) {
            BetaMissionUserStateEntity state = ensureUserState(parentUserId);
            fillEntryFromState(vo, state);
        } else {
            vo.setPackCompleted(false);
            vo.setContextLocked(false);
            vo.setPopupDismissed(false);
            vo.setRequiredDone(0);
        }
        return vo;
    }

    @Override
    public void assertBetaMissionAllowed(Long parentUserId) {
        if (!isGlobalBetaMissionEnabled() || !hasBetaAccess(parentUserId)) {
            throw new RenException(ErrorCode.PARENT_BETA_MISSION_DISABLED);
        }
    }

    @Override
    public BetaMissionOverviewVO getOverview(Long parentUserId) {
        assertBetaMissionAllowed(parentUserId);
        BetaMissionUserStateEntity state = ensureUserState(parentUserId);
        return buildOverview(parentUserId, state);
    }

    @Override
    public BetaMissionOverviewVO sync(Long parentUserId) {
        assertBetaMissionAllowed(parentUserId);
        BetaMissionUserStateEntity state = ensureUserState(parentUserId);
        runAutoSync(parentUserId, state);
        return buildOverview(parentUserId, state);
    }

    @Override
    public BetaMissionOverviewVO setContext(Long parentUserId, BetaMissionContextDTO dto) {
        assertBetaMissionAllowed(parentUserId);
        BetaMissionUserStateEntity state = ensureUserState(parentUserId);
        if (state.getContextChildId() != null) {
            throw new RenException(ErrorCode.PARENT_BETA_MISSION_CONTEXT_LOCKED);
        }
        Long childId = dto.getChildId();
        if (!isContextChildValid(parentUserId, childId)) {
            throw new RenException(ErrorCode.PARENT_BETA_MISSION_CONTEXT_INVALID);
        }
        state.setContextChildId(childId);
        state.setUpdateTime(new Date());
        betaMissionUserStateDao.updateById(state);
        runAutoSync(parentUserId, state);
        return buildOverview(parentUserId, state);
    }

    @Override
    public BetaMissionOverviewVO skipStep(Long parentUserId, String stepKey) {
        assertBetaMissionAllowed(parentUserId);
        StepDef step = BetaMissionStepRegistry.find(stepKey)
                .orElseThrow(() -> new RenException(ErrorCode.PARENT_BETA_MISSION_STEP_INVALID));
        if (step.isRequired()) {
            throw new RenException(ErrorCode.PARENT_BETA_MISSION_STEP_NOT_SKIPPABLE);
        }
        BetaMissionUserStateEntity state = ensureUserState(parentUserId);
        Map<String, String> states = parseStepStates(state);
        String current = states.getOrDefault(stepKey, BetaMissionStepRegistry.STATUS_PENDING);
        if (!BetaMissionStepRegistry.STATUS_COMPLETED.equals(current)) {
            states.put(stepKey, BetaMissionStepRegistry.STATUS_SKIPPED);
            saveStepStates(state, states);
            betaMissionUserStateDao.updateById(state);
        }
        return buildOverview(parentUserId, state);
    }

    @Override
    public void visitStep(Long parentUserId, String stepKey) {
        assertBetaMissionAllowed(parentUserId);
        StepDef step = BetaMissionStepRegistry.find(stepKey)
                .orElseThrow(() -> new RenException(ErrorCode.PARENT_BETA_MISSION_STEP_INVALID));
        if (!BetaMissionStepRegistry.VERIFY_VISIT.equals(step.getVerifyMode())) {
            throw new RenException(ErrorCode.PARENT_BETA_MISSION_STEP_INVALID);
        }
        BetaMissionUserStateEntity state = ensureUserState(parentUserId);
        state.setRiskAlertVisited(1);
        Map<String, String> states = parseStepStates(state);
        states.put(stepKey, BetaMissionStepRegistry.STATUS_COMPLETED);
        saveStepStates(state, states);
        betaMissionUserStateDao.updateById(state);
    }

    @Override
    public void dismissPopup(Long parentUserId) {
        assertBetaMissionAllowed(parentUserId);
        BetaMissionUserStateEntity state = ensureUserState(parentUserId);
        state.setPopupDismissed(1);
        state.setUpdateTime(new Date());
        betaMissionUserStateDao.updateById(state);
    }

    @Override
    public BetaMissionAdminConfigVO adminGetConfig() {
        BetaMissionAdminConfigVO vo = new BetaMissionAdminConfigVO();
        vo.setEnabled(isGlobalBetaMissionEnabled());
        vo.setCampaignCode(BetaMissionStepRegistry.CAMPAIGN_CODE);
        vo.setCampaignTitle(BetaMissionStepRegistry.CAMPAIGN_TITLE);
        vo.setStepCount(BetaMissionStepRegistry.allSteps().size());
        vo.setRequiredCount(BetaMissionStepRegistry.requiredCount());
        return vo;
    }

    @Override
    public void adminSaveConfig(BetaMissionAdminConfigSaveDTO dto) {
        String value = Boolean.TRUE.equals(dto.getEnabled()) ? "true" : "false";
        sysParamsService.updateValueByCode(PARAM_BETA_MISSION_ENABLED, value);
    }

    @Override
    public BetaMissionFunnelVO adminFunnel() {
        int betaTesterTotal = countBetaTesters();
        List<BetaMissionUserStateEntity> allStates = betaMissionUserStateDao.selectList(null);
        Map<String, int[]> stepCounts = new LinkedHashMap<>();
        for (StepDef step : BetaMissionStepRegistry.allSteps()) {
            stepCounts.put(step.getStepKey(), new int[] {0, 0});
        }
        int packCompletedTotal = 0;
        for (BetaMissionUserStateEntity state : allStates) {
            if (isPackCompleted(state)) {
                packCompletedTotal++;
            }
            Map<String, String> states = parseStepStates(state);
            for (StepDef step : BetaMissionStepRegistry.allSteps()) {
                String status = states.getOrDefault(step.getStepKey(), BetaMissionStepRegistry.STATUS_PENDING);
                int[] arr = stepCounts.get(step.getStepKey());
                if (BetaMissionStepRegistry.STATUS_COMPLETED.equals(status)) {
                    arr[0]++;
                } else if (BetaMissionStepRegistry.STATUS_SKIPPED.equals(status)) {
                    arr[1]++;
                }
            }
        }
        BetaMissionFunnelVO vo = new BetaMissionFunnelVO();
        vo.setBetaTesterTotal(betaTesterTotal);
        vo.setPackCompletedTotal(packCompletedTotal);
        List<BetaMissionFunnelStepVO> steps = new ArrayList<>();
        for (StepDef step : BetaMissionStepRegistry.allSteps()) {
            int[] arr = stepCounts.get(step.getStepKey());
            BetaMissionFunnelStepVO s = new BetaMissionFunnelStepVO();
            s.setStepKey(step.getStepKey());
            s.setTitle(step.getTitle());
            s.setRequired(step.isRequired());
            s.setCompletedCount(arr[0]);
            s.setSkippedCount(arr[1]);
            s.setCompletionRate(betaTesterTotal > 0 ? roundRate(arr[0], betaTesterTotal) : 0.0);
            steps.add(s);
        }
        vo.setSteps(steps);
        return vo;
    }

    @Override
    public PageData<BetaMissionUserProgressVO> adminUsers(Map<String, Object> params) {
        int page = parseInt(params.get("page"), 1);
        int limit = parseInt(params.get("limit"), 20);
        Boolean packCompletedFilter = parsePackCompletedFilter(params.get("packCompleted"));

        List<ParentUserEntity> allBeta = parentUserDao.selectList(
                new LambdaQueryWrapper<ParentUserEntity>()
                        .eq(ParentUserEntity::getIsBetaTester, 1)
                        .orderByDesc(ParentUserEntity::getUpdateTime));
        List<BetaMissionUserProgressVO> filtered = new ArrayList<>();
        for (ParentUserEntity user : allBeta) {
            BetaMissionUserStateEntity state = betaMissionUserStateDao.selectOne(
                    new LambdaQueryWrapper<BetaMissionUserStateEntity>()
                            .eq(BetaMissionUserStateEntity::getParentUserId, user.getId()));
            boolean packCompleted = state != null && isPackCompleted(state);
            if (packCompletedFilter != null && packCompleted != packCompletedFilter) {
                continue;
            }
            BetaMissionUserProgressVO vo = new BetaMissionUserProgressVO();
            vo.setParentUserId(user.getId());
            vo.setParentNickname(user.getNickname());
            if (state != null) {
                vo.setContextChildId(state.getContextChildId());
                vo.setRequiredDone(state.getRequiredDoneCount() != null ? state.getRequiredDoneCount() : 0);
                vo.setPackCompleted(packCompleted);
                vo.setUpdateTime(state.getUpdateTime());
            } else {
                vo.setRequiredDone(0);
                vo.setPackCompleted(false);
            }
            vo.setRequiredTotal(BetaMissionStepRegistry.requiredCount());
            filtered.add(vo);
        }
        int total = filtered.size();
        int from = Math.max(0, (page - 1) * limit);
        int to = Math.min(from + limit, total);
        List<BetaMissionUserProgressVO> pageList = from >= total ? List.of() : filtered.subList(from, to);
        return new PageData<>(pageList, total);
    }

    @Override
    public BetaMissionUserDetailVO adminUserDetail(Long parentUserId) {
        ParentUserEntity user = parentUserDao.selectById(parentUserId);
        if (user == null) {
            throw new RenException("家长不存在");
        }
        BetaMissionUserStateEntity state = betaMissionUserStateDao.selectOne(
                new LambdaQueryWrapper<BetaMissionUserStateEntity>()
                        .eq(BetaMissionUserStateEntity::getParentUserId, parentUserId));
        if (state == null) {
            state = new BetaMissionUserStateEntity();
            state.setParentUserId(parentUserId);
            state.setStepStates(defaultStepStatesJson());
        }
        BetaMissionUserDetailVO vo = new BetaMissionUserDetailVO();
        vo.setParentUserId(parentUserId);
        vo.setParentNickname(user.getNickname());
        vo.setContextChildId(state.getContextChildId());
        vo.setContextChildName(resolveChildName(state.getContextChildId()));
        vo.setPackCompleted(isPackCompleted(state));
        vo.setPackCompletedAt(state.getPackCompletedAt());
        vo.setPopupDismissed(state.getPopupDismissed() != null && state.getPopupDismissed() == 1);
        vo.setSteps(buildStepVos(parentUserId, state));
        return vo;
    }

    private void runAutoSync(Long parentUserId, BetaMissionUserStateEntity state) {
        Map<String, String> states = parseStepStates(state);
        boolean changed = false;
        for (StepDef step : BetaMissionStepRegistry.autoSteps()) {
            String current = states.getOrDefault(step.getStepKey(), BetaMissionStepRegistry.STATUS_PENDING);
            if (BetaMissionStepRegistry.STATUS_SKIPPED.equals(current)) {
                continue;
            }
            if (betaMissionVerifyService.isStepCompleted(parentUserId, step, state)) {
                if (!BetaMissionStepRegistry.STATUS_COMPLETED.equals(current)) {
                    states.put(step.getStepKey(), BetaMissionStepRegistry.STATUS_COMPLETED);
                    changed = true;
                }
            }
        }
        if (changed) {
            saveStepStates(state, states);
            betaMissionUserStateDao.updateById(state);
        }
    }

    private BetaMissionOverviewVO buildOverview(Long parentUserId, BetaMissionUserStateEntity state) {
        BetaMissionOverviewVO vo = new BetaMissionOverviewVO();
        vo.setCampaignCode(BetaMissionStepRegistry.CAMPAIGN_CODE);
        vo.setCampaignTitle(BetaMissionStepRegistry.CAMPAIGN_TITLE);
        vo.setCampaignDescription(BetaMissionStepRegistry.CAMPAIGN_DESCRIPTION);
        vo.setContextChildId(state.getContextChildId());
        vo.setContextChildName(resolveChildName(state.getContextChildId()));
        vo.setContextLocked(state.getContextChildId() != null);
        vo.setRequiredTotal(BetaMissionStepRegistry.requiredCount());
        vo.setRequiredDone(state.getRequiredDoneCount() != null ? state.getRequiredDoneCount() : 0);
        vo.setPackCompleted(isPackCompleted(state));
        vo.setPopupDismissed(state.getPopupDismissed() != null && state.getPopupDismissed() == 1);
        vo.setSections(buildSections(parentUserId, state));
        return vo;
    }

    private List<BetaMissionSectionVO> buildSections(Long parentUserId, BetaMissionUserStateEntity state) {
        Map<String, String> states = parseStepStates(state);
        String earliestDeviceId = resolveEarliestDeviceId(parentUserId);
        String contextChildDeviceId = resolveContextChildDeviceId(state.getContextChildId());
        Map<String, BetaMissionSectionVO> sectionMap = new LinkedHashMap<>();
        for (StepDef step : BetaMissionStepRegistry.allSteps()) {
            BetaMissionSectionVO section = sectionMap.computeIfAbsent(step.getSection(), code -> {
                BetaMissionSectionVO s = new BetaMissionSectionVO();
                s.setCode(code);
                s.setTitle(step.getSectionTitle());
                s.setSteps(new ArrayList<>());
                return s;
            });
            BetaMissionStepVO stepVo = new BetaMissionStepVO();
            stepVo.setStepKey(step.getStepKey());
            stepVo.setTitle(step.getTitle());
            stepVo.setDescription(step.getDescription());
            stepVo.setRequired(step.isRequired());
            stepVo.setVerifyMode(step.getVerifyMode());
            stepVo.setStatus(states.getOrDefault(step.getStepKey(), BetaMissionStepRegistry.STATUS_PENDING));
            stepVo.setNeedsContextChild(step.isNeedsContextChild());
            stepVo.setNavigateType(BetaMissionStepRegistry.NAVIGATE_TO);
            stepVo.setActionUrl(BetaMissionStepRegistry.buildActionUrl(
                    step, state.getContextChildId(), earliestDeviceId, contextChildDeviceId));
            section.getSteps().add(stepVo);
        }
        return new ArrayList<>(sectionMap.values());
    }

    private List<BetaMissionStepVO> buildStepVos(Long parentUserId, BetaMissionUserStateEntity state) {
        List<BetaMissionSectionVO> sections = buildSections(parentUserId, state);
        List<BetaMissionStepVO> steps = new ArrayList<>();
        for (BetaMissionSectionVO section : sections) {
            steps.addAll(section.getSteps());
        }
        return steps;
    }

    private void fillEntryFromState(BetaMissionEntryStatusVO vo, BetaMissionUserStateEntity state) {
        vo.setPackCompleted(isPackCompleted(state));
        vo.setContextLocked(state.getContextChildId() != null);
        vo.setPopupDismissed(state.getPopupDismissed() != null && state.getPopupDismissed() == 1);
        vo.setRequiredDone(state.getRequiredDoneCount() != null ? state.getRequiredDoneCount() : 0);
    }

    private BetaMissionUserStateEntity ensureUserState(Long parentUserId) {
        BetaMissionUserStateEntity state = betaMissionUserStateDao.selectOne(
                new LambdaQueryWrapper<BetaMissionUserStateEntity>()
                        .eq(BetaMissionUserStateEntity::getParentUserId, parentUserId));
        if (state != null) {
            maybeInheritHouseholdContext(parentUserId, state);
            return state;
        }
        state = new BetaMissionUserStateEntity();
        state.setParentUserId(parentUserId);
        state.setCampaignCode(BetaMissionStepRegistry.CAMPAIGN_CODE);
        state.setStepStates(defaultStepStatesJson());
        state.setRequiredDoneCount(0);
        state.setPopupDismissed(0);
        state.setRiskAlertVisited(0);
        state.setCreateTime(new Date());
        state.setUpdateTime(new Date());
        try {
            betaMissionUserStateDao.insert(state);
        } catch (DuplicateKeyException ex) {
            state = betaMissionUserStateDao.selectOne(
                    new LambdaQueryWrapper<BetaMissionUserStateEntity>()
                            .eq(BetaMissionUserStateEntity::getParentUserId, parentUserId));
            if (state == null) {
                throw ex;
            }
        }
        maybeInheritHouseholdContext(parentUserId, state);
        return state;
    }

    private void maybeInheritHouseholdContext(Long parentUserId, BetaMissionUserStateEntity state) {
        if (state == null || state.getContextChildId() != null) {
            return;
        }
        Long inherited = ParentBetaAccessHelper.findHouseholdContextChildId(
                parentDeviceBindingDao, betaMissionUserStateDao, parentUserDao, parentUserId);
        if (inherited == null || !isContextChildValid(parentUserId, inherited)) {
            return;
        }
        state.setContextChildId(inherited);
        state.setUpdateTime(new Date());
        betaMissionUserStateDao.updateById(state);
        runAutoSync(parentUserId, state);
    }

    private boolean hasBetaAccess(Long parentUserId) {
        return ParentBetaAccessHelper.hasBetaAccess(parentUserDao, parentDeviceBindingDao, parentUserId);
    }

    private void saveStepStates(BetaMissionUserStateEntity state, Map<String, String> states) {
        try {
            state.setStepStates(objectMapper.writeValueAsString(states));
        } catch (Exception e) {
            throw new RenException("步骤状态序列化失败");
        }
        recalculatePack(state, states);
        state.setUpdateTime(new Date());
    }

    private void recalculatePack(BetaMissionUserStateEntity state, Map<String, String> states) {
        int requiredDone = 0;
        for (StepDef step : BetaMissionStepRegistry.allSteps()) {
            if (step.isRequired()
                    && BetaMissionStepRegistry.STATUS_COMPLETED.equals(states.get(step.getStepKey()))) {
                requiredDone++;
            }
        }
        state.setRequiredDoneCount(requiredDone);
        if (requiredDone >= BetaMissionStepRegistry.requiredCount() && state.getPackCompletedAt() == null) {
            state.setPackCompletedAt(new Date());
        }
    }

    private boolean isPackCompleted(BetaMissionUserStateEntity state) {
        if (state.getPackCompletedAt() != null) {
            return true;
        }
        if (state.getRequiredDoneCount() != null
                && state.getRequiredDoneCount() >= BetaMissionStepRegistry.requiredCount()) {
            return true;
        }
        Map<String, String> states = parseStepStates(state);
        int requiredDone = 0;
        for (StepDef step : BetaMissionStepRegistry.allSteps()) {
            if (step.isRequired()
                    && BetaMissionStepRegistry.STATUS_COMPLETED.equals(states.get(step.getStepKey()))) {
                requiredDone++;
            }
        }
        return requiredDone >= BetaMissionStepRegistry.requiredCount();
    }

    private Map<String, String> parseStepStates(BetaMissionUserStateEntity state) {
        if (state == null || StringUtils.isBlank(state.getStepStates())) {
            return defaultStepStatesMap();
        }
        try {
            Map<String, String> map = objectMapper.readValue(
                    state.getStepStates(), new TypeReference<Map<String, String>>() {});
            for (StepDef step : BetaMissionStepRegistry.allSteps()) {
                map.putIfAbsent(step.getStepKey(), BetaMissionStepRegistry.STATUS_PENDING);
            }
            return map;
        } catch (Exception e) {
            return defaultStepStatesMap();
        }
    }

    private static Map<String, String> defaultStepStatesMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (StepDef step : BetaMissionStepRegistry.allSteps()) {
            map.put(step.getStepKey(), BetaMissionStepRegistry.STATUS_PENDING);
        }
        return map;
    }

    private static String defaultStepStatesJson() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (StepDef step : BetaMissionStepRegistry.allSteps()) {
            if (!first) {
                sb.append(',');
            }
            sb.append('"').append(step.getStepKey()).append("\":\"pending\"");
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }

    private String resolveEarliestDeviceId(Long parentUserId) {
        List<ParentDeviceBindingEntity> bindings = parentDeviceBindingDao.selectList(
                new LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .orderByAsc(ParentDeviceBindingEntity::getBindTime)
                        .orderByAsc(ParentDeviceBindingEntity::getCreateTime)
                        .last("LIMIT 1"));
        if (bindings.isEmpty()) {
            return null;
        }
        return bindings.get(0).getDeviceId();
    }

    private String resolveContextChildDeviceId(Long contextChildId) {
        if (contextChildId == null) {
            return null;
        }
        DeviceChildEntity child = deviceChildDao.selectById(contextChildId);
        return child != null ? child.getDeviceId() : null;
    }

    private boolean isContextChildValid(Long parentUserId, Long childId) {
        if (childId == null) {
            return false;
        }
        try {
            ParentChildAccessHelper.ensureParentCanAccessChildById(
                    deviceChildDao, parentDeviceBindingDao, parentUserId, childId);
            return true;
        } catch (RenException e) {
            return false;
        }
    }

    private String resolveChildName(Long childId) {
        if (childId == null) {
            return null;
        }
        DeviceChildEntity child = deviceChildDao.selectById(childId);
        return child != null ? child.getName() : null;
    }

    private boolean isGlobalBetaMissionEnabled() {
        String v = sysParamsService.getValue(PARAM_BETA_MISSION_ENABLED, true);
        return "true".equalsIgnoreCase(StringUtils.trimToEmpty(v));
    }

    private int countBetaTesters() {
        Long count = parentUserDao.selectCount(
                new LambdaQueryWrapper<ParentUserEntity>().eq(ParentUserEntity::getIsBetaTester, 1));
        return count != null ? count.intValue() : 0;
    }

    private static double roundRate(int completed, int total) {
        return Math.round(completed * 10000.0 / total) / 10000.0;
    }

    private static int parseInt(Object v, int defaultVal) {
        if (v == null) {
            return defaultVal;
        }
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static Boolean parsePackCompletedFilter(Object v) {
        if (v == null || StringUtils.isBlank(String.valueOf(v))) {
            return null;
        }
        String s = String.valueOf(v).trim();
        if ("1".equals(s) || "true".equalsIgnoreCase(s)) {
            return true;
        }
        if ("0".equals(s) || "false".equalsIgnoreCase(s)) {
            return false;
        }
        return null;
    }
}
