package xiaozhi.modules.parent.betaconfidentiality.service.impl;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import xiaozhi.modules.parent.betaconfidentiality.dao.ParentBetaConfDocumentDao;
import xiaozhi.modules.parent.betaconfidentiality.dao.ParentBetaConfRecordDao;
import xiaozhi.modules.parent.betaconfidentiality.dto.ParentBetaConfAdminPublishDTO;
import xiaozhi.modules.parent.betaconfidentiality.dto.ParentBetaConfAdminSettingsDTO;
import xiaozhi.modules.parent.betaconfidentiality.dto.ParentBetaConfAgreeDTO;
import xiaozhi.modules.parent.betaconfidentiality.entity.ParentBetaConfDocumentEntity;
import xiaozhi.modules.parent.betaconfidentiality.entity.ParentBetaConfRecordEntity;
import xiaozhi.modules.parent.betaconfidentiality.service.ParentBetaConfService;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfAdminOverviewVO;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfDocumentVO;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfHistoryItemVO;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfPendingUserVO;
import xiaozhi.modules.parent.betaconfidentiality.vo.ParentBetaConfStatusVO;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.sys.service.SysParamsService;

@Service
@RequiredArgsConstructor
public class ParentBetaConfServiceImpl implements ParentBetaConfService {

    private static final String PARAM_ENABLED = "parent.beta_conf.enabled";

    private final ParentBetaConfDocumentDao parentBetaConfDocumentDao;
    private final ParentBetaConfRecordDao parentBetaConfRecordDao;
    private final ParentUserDao parentUserDao;
    private final SysParamsService sysParamsService;

    @Override
    public ParentBetaConfDocumentVO getPublishedDocument() {
        ParentBetaConfDocumentEntity doc = findPublished();
        if (doc == null) {
            return null;
        }
        return toDocumentVo(doc);
    }

    @Override
    public ParentBetaConfStatusVO getStatus(Long parentUserId) {
        ParentBetaConfStatusVO vo = new ParentBetaConfStatusVO();
        boolean enabled = isBetaConfEnabled();
        vo.setBetaConfEnabled(enabled);
        ParentBetaConfDocumentEntity doc = findPublished();
        if (!enabled || doc == null) {
            vo.setBetaConfRequired(false);
            vo.setBlocking(false);
            return vo;
        }
        vo.setCurrentVersion(doc.getVersion());
        vo.setTitle(doc.getTitle());
        vo.setSummary(doc.getSummary());
        ParentBetaConfRecordEntity record = findAgreedRecord(parentUserId, doc.getVersion());
        if (record != null) {
            vo.setAgreedVersion(record.getVersion());
            vo.setAgreedAt(record.getAgreedAt());
            vo.setBetaConfRequired(false);
            vo.setBlocking(false);
        } else {
            vo.setBetaConfRequired(true);
            vo.setBlocking(true);
        }
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void agree(Long parentUserId, ParentBetaConfAgreeDTO dto, String clientIp, String userAgent) {
        ParentBetaConfDocumentEntity doc = findPublished();
        if (doc == null) {
            throw new RenException(ErrorCode.PARENT_BETA_CONF_VERSION_INVALID);
        }
        String version = StringUtils.trimToEmpty(dto.getVersion());
        if (!StringUtils.equals(version, doc.getVersion())) {
            throw new RenException(ErrorCode.PARENT_BETA_CONF_VERSION_INVALID);
        }
        if (findAgreedRecord(parentUserId, version) != null) {
            return;
        }
        ParentBetaConfRecordEntity row = new ParentBetaConfRecordEntity();
        row.setParentUserId(parentUserId);
        row.setVersion(version);
        row.setAgreedAt(new Date());
        row.setChannel(ParentBetaConfRecordEntity.CHANNEL_WECHAT_MINIPROGRAM);
        row.setClientIp(StringUtils.left(StringUtils.trimToNull(clientIp), 64));
        row.setUserAgent(StringUtils.left(StringUtils.trimToNull(userAgent), 512));
        try {
            parentBetaConfRecordDao.insert(row);
        } catch (DuplicateKeyException ex) {
            // 并发幂等
        }
    }

    @Override
    public boolean isBetaConfEnabled() {
        String v = sysParamsService.getValue(PARAM_ENABLED, true);
        return "true".equalsIgnoreCase(StringUtils.trimToEmpty(v));
    }

    @Override
    public boolean isBetaConfRequired(Long parentUserId) {
        if (!isBetaConfEnabled() || parentUserId == null) {
            return false;
        }
        ParentBetaConfDocumentEntity doc = findPublished();
        if (doc == null) {
            return false;
        }
        return findAgreedRecord(parentUserId, doc.getVersion()) == null;
    }

    @Override
    public ParentBetaConfAdminOverviewVO adminOverview() {
        ParentBetaConfDocumentEntity doc = findPublished();
        ParentBetaConfAdminOverviewVO vo = new ParentBetaConfAdminOverviewVO();
        vo.setEnabled(isBetaConfEnabled());
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
    public void adminSaveSettings(ParentBetaConfAdminSettingsDTO dto) {
        if (dto.getEnabled() != null) {
            sysParamsService.updateValueByCode(PARAM_ENABLED, dto.getEnabled() ? "true" : "false");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminPublish(ParentBetaConfAdminPublishDTO dto) {
        String title = StringUtils.trimToEmpty(dto.getTitle());
        String summary = StringUtils.trimToEmpty(dto.getSummary());
        String content = StringUtils.trimToEmpty(dto.getContent());
        if (StringUtils.isBlank(title) || StringUtils.isBlank(summary) || StringUtils.isBlank(content)) {
            throw new RenException("标题、摘要、正文均不能为空");
        }
        ParentBetaConfDocumentEntity current = findPublished();
        if (current != null) {
            parentBetaConfDocumentDao.update(null, new LambdaUpdateWrapper<ParentBetaConfDocumentEntity>()
                    .eq(ParentBetaConfDocumentEntity::getId, current.getId())
                    .set(ParentBetaConfDocumentEntity::getStatus, ParentBetaConfDocumentEntity.STATUS_ARCHIVED)
                    .set(ParentBetaConfDocumentEntity::getUpdateTime, new Date()));
        }
        String newVersion = nextVersionString();
        Date now = new Date();
        ParentBetaConfDocumentEntity published = new ParentBetaConfDocumentEntity();
        published.setVersion(newVersion);
        published.setTitle(title);
        published.setSummary(summary);
        published.setContent(content);
        published.setStatus(ParentBetaConfDocumentEntity.STATUS_PUBLISHED);
        published.setPublishedAt(now);
        published.setCreateTime(now);
        published.setUpdateTime(now);
        parentBetaConfDocumentDao.insert(published);
    }

    @Override
    public List<ParentBetaConfHistoryItemVO> adminHistory() {
        List<ParentBetaConfDocumentEntity> list = parentBetaConfDocumentDao.selectList(
                new LambdaQueryWrapper<ParentBetaConfDocumentEntity>()
                        .orderByDesc(ParentBetaConfDocumentEntity::getPublishedAt)
                        .orderByDesc(ParentBetaConfDocumentEntity::getId));
        List<ParentBetaConfHistoryItemVO> result = new ArrayList<>();
        for (ParentBetaConfDocumentEntity e : list) {
            ParentBetaConfHistoryItemVO vo = new ParentBetaConfHistoryItemVO();
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
    public PageData<ParentBetaConfPendingUserVO> adminPendingUsers(Map<String, Object> params) {
        ParentBetaConfDocumentEntity doc = findPublished();
        if (doc == null) {
            return new PageData<>(List.of(), 0);
        }
        int page = parseInt(params, "page", 1);
        int limit = parseInt(params, "limit", 20);
        String currentVersion = doc.getVersion();
        LambdaQueryWrapper<ParentUserEntity> q = new LambdaQueryWrapper<ParentUserEntity>()
                .apply(
                        "NOT EXISTS (SELECT 1 FROM parent_beta_conf_record r "
                                + "WHERE r.parent_user_id = parent_user.id AND r.version = {0})",
                        currentVersion)
                .orderByDesc(ParentUserEntity::getId);
        Page<ParentUserEntity> pg = parentUserDao.selectPage(new Page<>(page, limit), q);
        List<ParentBetaConfPendingUserVO> list = new ArrayList<>();
        for (ParentUserEntity u : pg.getRecords()) {
            ParentBetaConfPendingUserVO vo = new ParentBetaConfPendingUserVO();
            vo.setParentUserId(u.getId());
            vo.setNickname(u.getNickname());
            vo.setCreateTime(u.getCreateTime());
            list.add(vo);
        }
        return new PageData<>(list, (int) pg.getTotal());
    }

    private ParentBetaConfDocumentEntity findPublished() {
        return parentBetaConfDocumentDao.selectOne(
                new LambdaQueryWrapper<ParentBetaConfDocumentEntity>()
                        .eq(ParentBetaConfDocumentEntity::getStatus, ParentBetaConfDocumentEntity.STATUS_PUBLISHED)
                        .orderByDesc(ParentBetaConfDocumentEntity::getPublishedAt)
                        .last("LIMIT 1"));
    }

    private ParentBetaConfRecordEntity findAgreedRecord(Long parentUserId, String version) {
        if (parentUserId == null || StringUtils.isBlank(version)) {
            return null;
        }
        return parentBetaConfRecordDao.selectOne(
                new LambdaQueryWrapper<ParentBetaConfRecordEntity>()
                        .eq(ParentBetaConfRecordEntity::getParentUserId, parentUserId)
                        .eq(ParentBetaConfRecordEntity::getVersion, version));
    }

    private int countParentUsers() {
        Long count = parentUserDao.selectCount(new LambdaQueryWrapper<>());
        return count != null ? count.intValue() : 0;
    }

    private int countAgreedForVersion(String version) {
        Long count = parentBetaConfRecordDao.selectCount(
                new LambdaQueryWrapper<ParentBetaConfRecordEntity>()
                        .eq(ParentBetaConfRecordEntity::getVersion, version));
        return count != null ? count.intValue() : 0;
    }

    private String nextVersionString() {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = day + "_v";
        List<ParentBetaConfDocumentEntity> today = parentBetaConfDocumentDao.selectList(
                new LambdaQueryWrapper<ParentBetaConfDocumentEntity>()
                        .likeRight(ParentBetaConfDocumentEntity::getVersion, prefix));
        int max = 0;
        for (ParentBetaConfDocumentEntity e : today) {
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

    private static ParentBetaConfDocumentVO toDocumentVo(ParentBetaConfDocumentEntity doc) {
        ParentBetaConfDocumentVO vo = new ParentBetaConfDocumentVO();
        vo.setVersion(doc.getVersion());
        vo.setCurrentVersion(doc.getVersion());
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
