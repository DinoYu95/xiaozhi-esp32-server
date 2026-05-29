package xiaozhi.modules.parent.service.impl;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.modules.parent.dao.ParentFeedbackDao;
import xiaozhi.modules.parent.dao.ParentUserDao;
import xiaozhi.modules.parent.dto.ParentFeedbackAdminNoteDTO;
import xiaozhi.modules.parent.dto.ParentFeedbackAdminStatusDTO;
import xiaozhi.modules.parent.dto.ParentFeedbackCreateDTO;
import xiaozhi.modules.parent.entity.ParentFeedbackEntity;
import xiaozhi.modules.parent.entity.ParentUserEntity;
import xiaozhi.modules.parent.service.ParentFeedbackService;
import xiaozhi.modules.parent.vo.ParentFeedbackAdminVO;
import xiaozhi.modules.parent.vo.ParentFeedbackDetailVO;
import xiaozhi.modules.parent.vo.ParentFeedbackEnabledVO;
import xiaozhi.modules.parent.vo.ParentFeedbackVO;
import xiaozhi.modules.sys.service.SysParamsService;

@Service
@RequiredArgsConstructor
public class ParentFeedbackServiceImpl implements ParentFeedbackService {

    private static final String PARAM_BETA_FEEDBACK_ENABLED = "server.beta_feedback_enabled";
    private static final Set<String> CATEGORIES = Set.of(
            "device_bind", "child_voiceprint", "chat_voice", "skill", "shadow_mission", "other");
    private static final Set<String> STATUSES = Set.of(
            ParentFeedbackEntity.STATUS_PENDING,
            ParentFeedbackEntity.STATUS_PROCESSING,
            ParentFeedbackEntity.STATUS_RESOLVED,
            ParentFeedbackEntity.STATUS_WONT_FIX);
    private static final int DESCRIPTION_MAX = 2000;
    private static final int MAX_IMAGES = 3;
    private static final long FEEDBACK_IMAGE_MAX_BYTES = 5 * 1024 * 1024;
    private static final Set<String> IMAGE_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private final ParentFeedbackDao parentFeedbackDao;
    private final ParentUserDao parentUserDao;
    private final SysParamsService sysParamsService;
    private final ObjectMapper objectMapper;

    @Override
    public ParentFeedbackEnabledVO getEntryStatus(Long parentUserId) {
        boolean global = isGlobalBetaFeedbackEnabled();
        boolean beta = isBetaTester(parentUserId);
        ParentFeedbackEnabledVO vo = new ParentFeedbackEnabledVO();
        vo.setBetaFeedbackEnabled(global);
        vo.setBetaTester(beta);
        vo.setShowEntry(global && beta);
        return vo;
    }

    @Override
    public void assertBetaFeedbackAllowed(Long parentUserId) {
        if (!isGlobalBetaFeedbackEnabled() || !isBetaTester(parentUserId)) {
            throw new RenException(ErrorCode.PARENT_BETA_FEEDBACK_DISABLED);
        }
    }

    @Override
    public ParentFeedbackVO create(Long parentUserId, ParentFeedbackCreateDTO dto) {
        assertBetaFeedbackAllowed(parentUserId);
        String category = StringUtils.trimToEmpty(dto.getCategory()).toLowerCase(Locale.ROOT);
        if (!CATEGORIES.contains(category)) {
            throw new RenException("无效的问题类型");
        }
        String desc = StringUtils.trimToEmpty(dto.getDescription());
        if (StringUtils.isBlank(desc)) {
            throw new RenException("请填写问题描述");
        }
        if (desc.length() > DESCRIPTION_MAX) {
            throw new RenException("问题描述不超过 " + DESCRIPTION_MAX + " 字");
        }
        List<String> images = normalizeImageUrls(dto.getImageUrls());
        if (images.size() > MAX_IMAGES) {
            throw new RenException("截图最多 " + MAX_IMAGES + " 张");
        }
        ParentFeedbackEntity row = new ParentFeedbackEntity();
        row.setParentUserId(parentUserId);
        row.setCategory(category);
        row.setDescription(desc);
        row.setBlocking(Boolean.TRUE.equals(dto.getBlocking()) ? 1 : 0);
        row.setAllowContact(Boolean.TRUE.equals(dto.getAllowContact()) ? 1 : 0);
        row.setStatus(ParentFeedbackEntity.STATUS_PENDING);
        if (dto.getContextSnapshot() != null && !dto.getContextSnapshot().isEmpty()) {
            try {
                row.setContextSnapshot(objectMapper.writeValueAsString(dto.getContextSnapshot()));
            } catch (Exception e) {
                throw new RenException("contextSnapshot 格式无效");
            }
        }
        if (!images.isEmpty()) {
            try {
                row.setImageUrls(objectMapper.writeValueAsString(images));
            } catch (Exception e) {
                throw new RenException("imageUrls 格式无效");
            }
        }
        row.setCreateTime(new Date());
        row.setUpdateTime(new Date());
        parentFeedbackDao.insert(row);
        row.setFeedbackNo(buildFeedbackNo(row.getId()));
        parentFeedbackDao.updateById(row);
        return toListVo(row);
    }

    @Override
    public String storeFeedbackImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RenException(ErrorCode.UPLOAD_FILE_EMPTY);
        }
        if (file.getSize() > FEEDBACK_IMAGE_MAX_BYTES) {
            throw new RenException("截图不能超过 5MB");
        }
        String ext = resolveImageExtension(file.getOriginalFilename());
        if (ext == null) {
            ext = resolveImageExtensionFromContentType(file.getContentType());
        }
        if (ext == null) {
            throw new RenException("仅支持 jpg、jpeg、png、gif、webp 图片");
        }
        String storedName = UUID.randomUUID().toString().toLowerCase(Locale.ROOT) + "." + ext;
        Path dirAbs = Paths.get("uploadfile", "parent-feedback").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dirAbs);
            Path target = dirAbs.resolve(storedName).normalize();
            if (!target.startsWith(dirAbs)) {
                throw new RenException(ErrorCode.UPLOAD_FILE_ERROR);
            }
            file.transferTo(target.toFile());
        } catch (IOException e) {
            throw new RenException(ErrorCode.UPLOAD_FILE_ERROR, e);
        }
        return storedName;
    }

    @Override
    public PageData<ParentFeedbackVO> pageByParent(Long parentUserId, int page, int limit) {
        assertBetaFeedbackAllowed(parentUserId);
        int p = Math.max(1, page);
        int l = Math.min(50, Math.max(1, limit));
        Page<ParentFeedbackEntity> pg = parentFeedbackDao.selectPage(
                new Page<>(p, l),
                new LambdaQueryWrapper<ParentFeedbackEntity>()
                        .eq(ParentFeedbackEntity::getParentUserId, parentUserId)
                        .orderByDesc(ParentFeedbackEntity::getId));
        List<ParentFeedbackVO> list = new ArrayList<>();
        if (pg.getRecords() != null) {
            for (ParentFeedbackEntity e : pg.getRecords()) {
                list.add(toListVo(e));
            }
        }
        return new PageData<>(list, pg.getTotal());
    }

    @Override
    public ParentFeedbackDetailVO getByParent(Long parentUserId, Long id) {
        assertBetaFeedbackAllowed(parentUserId);
        ParentFeedbackEntity e = parentFeedbackDao.selectById(id);
        if (e == null || !parentUserId.equals(e.getParentUserId())) {
            throw new RenException(ErrorCode.PARENT_FEEDBACK_NOT_FOUND);
        }
        return toDetailVo(e);
    }

    @Override
    public PageData<ParentFeedbackAdminVO> adminPage(Map<String, Object> params) {
        int page = parseInt(params, "page", 1);
        int limit = Math.min(100, parseInt(params, "limit", 20));
        LambdaQueryWrapper<ParentFeedbackEntity> q = new LambdaQueryWrapper<>();
        String status = strParam(params, "status");
        if (StringUtils.isNotBlank(status)) {
            q.eq(ParentFeedbackEntity::getStatus, status.trim().toLowerCase(Locale.ROOT));
        }
        String category = strParam(params, "category");
        if (StringUtils.isNotBlank(category)) {
            q.eq(ParentFeedbackEntity::getCategory, category.trim().toLowerCase(Locale.ROOT));
        }
        String blocking = strParam(params, "blocking");
        if ("1".equals(blocking) || "true".equalsIgnoreCase(blocking)) {
            q.eq(ParentFeedbackEntity::getBlocking, 1);
        } else if ("0".equals(blocking) || "false".equalsIgnoreCase(blocking)) {
            q.eq(ParentFeedbackEntity::getBlocking, 0);
        }
        Long parentUserId = longParam(params, "parentUserId");
        if (parentUserId != null) {
            q.eq(ParentFeedbackEntity::getParentUserId, parentUserId);
        }
        String feedbackNo = strParam(params, "feedbackNo");
        if (StringUtils.isNotBlank(feedbackNo)) {
            q.like(ParentFeedbackEntity::getFeedbackNo, feedbackNo.trim());
        }
        q.orderByDesc(ParentFeedbackEntity::getId);
        Page<ParentFeedbackEntity> pg = parentFeedbackDao.selectPage(new Page<>(page, limit), q);
        List<ParentFeedbackAdminVO> list = new ArrayList<>();
        if (pg.getRecords() != null) {
            for (ParentFeedbackEntity e : pg.getRecords()) {
                list.add(toAdminVo(e, false));
            }
        }
        return new PageData<>(list, pg.getTotal());
    }

    @Override
    public ParentFeedbackAdminVO adminGet(Long id) {
        ParentFeedbackEntity e = parentFeedbackDao.selectById(id);
        if (e == null) {
            throw new RenException(ErrorCode.PARENT_FEEDBACK_NOT_FOUND);
        }
        return toAdminVo(e, true);
    }

    @Override
    public void adminUpdateStatus(Long id, ParentFeedbackAdminStatusDTO dto) {
        ParentFeedbackEntity e = parentFeedbackDao.selectById(id);
        if (e == null) {
            throw new RenException(ErrorCode.PARENT_FEEDBACK_NOT_FOUND);
        }
        String status = StringUtils.trimToEmpty(dto.getStatus()).toLowerCase(Locale.ROOT);
        if (!STATUSES.contains(status)) {
            throw new RenException("无效的状态");
        }
        e.setStatus(status);
        if (StringUtils.isNotBlank(dto.getAdminNote())) {
            e.setAdminNote(dto.getAdminNote().trim());
        }
        if (ParentFeedbackEntity.STATUS_WONT_FIX.equals(status)) {
            e.setWontFixReason(StringUtils.trimToNull(dto.getWontFixReason()));
        } else if (StringUtils.isNotBlank(dto.getWontFixReason())) {
            e.setWontFixReason(dto.getWontFixReason().trim());
        }
        e.setUpdateTime(new Date());
        parentFeedbackDao.updateById(e);
    }

    @Override
    public void adminUpdateNote(Long id, ParentFeedbackAdminNoteDTO dto) {
        ParentFeedbackEntity e = parentFeedbackDao.selectById(id);
        if (e == null) {
            throw new RenException(ErrorCode.PARENT_FEEDBACK_NOT_FOUND);
        }
        e.setAdminNote(dto.getAdminNote().trim());
        e.setUpdateTime(new Date());
        parentFeedbackDao.updateById(e);
    }

    @Override
    public void adminSetBetaTester(Long parentUserId, boolean betaTester) {
        ParentUserEntity user = parentUserDao.selectById(parentUserId);
        if (user == null) {
            throw new RenException("家长用户不存在");
        }
        user.setIsBetaTester(betaTester ? 1 : 0);
        user.setUpdateTime(new Date());
        parentUserDao.updateById(user);
    }

    private boolean isGlobalBetaFeedbackEnabled() {
        String v = sysParamsService.getValue(PARAM_BETA_FEEDBACK_ENABLED, true);
        return "true".equalsIgnoreCase(StringUtils.trimToEmpty(v));
    }

    private boolean isBetaTester(Long parentUserId) {
        if (parentUserId == null) {
            return false;
        }
        ParentUserEntity user = parentUserDao.selectById(parentUserId);
        return user != null && user.getIsBetaTester() != null && user.getIsBetaTester() == 1;
    }

    private static String buildFeedbackNo(Long id) {
        String day = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "FB-" + day + "-" + String.format("%05d", id == null ? 0 : id);
    }

    private ParentFeedbackVO toListVo(ParentFeedbackEntity e) {
        ParentFeedbackVO vo = new ParentFeedbackVO();
        vo.setId(e.getId());
        vo.setFeedbackNo(e.getFeedbackNo());
        vo.setCategory(e.getCategory());
        vo.setDescription(e.getDescription());
        vo.setBlocking(e.getBlocking() != null && e.getBlocking() == 1);
        vo.setAllowContact(e.getAllowContact() != null && e.getAllowContact() == 1);
        vo.setStatus(e.getStatus());
        vo.setImageUrls(parseImageUrls(e.getImageUrls()));
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
    }

    private ParentFeedbackDetailVO toDetailVo(ParentFeedbackEntity e) {
        ParentFeedbackDetailVO vo = new ParentFeedbackDetailVO();
        ParentFeedbackVO base = toListVo(e);
        vo.setId(base.getId());
        vo.setFeedbackNo(base.getFeedbackNo());
        vo.setCategory(base.getCategory());
        vo.setDescription(base.getDescription());
        vo.setBlocking(base.getBlocking());
        vo.setAllowContact(base.getAllowContact());
        vo.setStatus(base.getStatus());
        vo.setImageUrls(base.getImageUrls());
        vo.setCreateTime(base.getCreateTime());
        vo.setUpdateTime(base.getUpdateTime());
        vo.setContextSnapshot(parseContext(e.getContextSnapshot()));
        return vo;
    }

    private ParentFeedbackAdminVO toAdminVo(ParentFeedbackEntity e, boolean includeContext) {
        ParentFeedbackAdminVO vo = new ParentFeedbackAdminVO();
        vo.setId(e.getId());
        vo.setFeedbackNo(e.getFeedbackNo());
        vo.setParentUserId(e.getParentUserId());
        ParentUserEntity pu = parentUserDao.selectById(e.getParentUserId());
        vo.setParentNickname(pu != null ? pu.getNickname() : null);
        vo.setCategory(e.getCategory());
        vo.setDescription(e.getDescription());
        vo.setBlocking(e.getBlocking() != null && e.getBlocking() == 1);
        vo.setAllowContact(e.getAllowContact() != null && e.getAllowContact() == 1);
        vo.setStatus(e.getStatus());
        vo.setImageUrls(parseImageUrls(e.getImageUrls()));
        vo.setAdminNote(e.getAdminNote());
        vo.setWontFixReason(e.getWontFixReason());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        if (includeContext) {
            vo.setContextSnapshot(parseContext(e.getContextSnapshot()));
        }
        return vo;
    }

    private Map<String, Object> parseContext(String json) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return null;
        }
    }

    private List<String> parseImageUrls(String json) {
        if (StringUtils.isBlank(json)) {
            return List.of();
        }
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return list == null ? List.of() : list;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> normalizeImageUrls(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String u : raw) {
            if (StringUtils.isNotBlank(u)) {
                set.add(u.trim());
            }
        }
        return new ArrayList<>(set);
    }

    private static int parseInt(Map<String, Object> params, String key, int def) {
        if (params == null || params.get(key) == null) {
            return def;
        }
        try {
            return Integer.parseInt(params.get(key).toString());
        } catch (Exception e) {
            return def;
        }
    }

    private static Long longParam(Map<String, Object> params, String key) {
        if (params == null || params.get(key) == null) {
            return null;
        }
        try {
            return Long.parseLong(params.get(key).toString());
        } catch (Exception e) {
            return null;
        }
    }

    private static String strParam(Map<String, Object> params, String key) {
        if (params == null || params.get(key) == null) {
            return null;
        }
        return params.get(key).toString();
    }

    private static String resolveImageExtension(String originalFilename) {
        if (StringUtils.isBlank(originalFilename) || !originalFilename.contains(".")) {
            return null;
        }
        String ext = originalFilename.substring(originalFilename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        return IMAGE_EXT.contains(ext) ? ext : null;
    }

    private static String resolveImageExtensionFromContentType(String contentType) {
        if (StringUtils.isBlank(contentType)) {
            return null;
        }
        String ct = contentType.toLowerCase(Locale.ROOT);
        if (ct.contains("png")) {
            return "png";
        }
        if (ct.contains("gif")) {
            return "gif";
        }
        if (ct.contains("webp")) {
            return "webp";
        }
        if (ct.contains("jpeg") || ct.contains("jpg")) {
            return "jpg";
        }
        return null;
    }
}
