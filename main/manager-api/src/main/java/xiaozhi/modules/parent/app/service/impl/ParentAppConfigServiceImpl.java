package xiaozhi.modules.parent.app.service.impl;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.RenException;
import xiaozhi.modules.parent.app.dto.ParentAppAdminPublicConfigSaveDTO;
import xiaozhi.modules.parent.app.dto.ParentAppAdminSettingsSaveDTO;
import xiaozhi.modules.parent.app.service.ParentAppConfigService;
import xiaozhi.modules.parent.app.vo.ParentAppAdminSettingsVO;
import xiaozhi.modules.parent.app.vo.ParentAppGuestPreviewVO;
import xiaozhi.modules.parent.app.vo.ParentAppPublicConfigVO;
import xiaozhi.modules.sys.service.SysParamsService;

@Service
@RequiredArgsConstructor
public class ParentAppConfigServiceImpl implements ParentAppConfigService {

    static final String PARAM_ACCESS_MODE = "parent.app.access_mode";
    static final String PARAM_REVIEW_MODE = "parent.app.review_mode";
    static final String PARAM_PUBLIC_CONFIG = "parent.app.public_config";

    private static final String DEFAULT_ACCESS_MODE = "internal_beta";
    private static final ZoneId ZONE_CN = ZoneId.of("Asia/Shanghai");

    private final SysParamsService sysParamsService;
    private final ObjectMapper objectMapper;

    @Override
    public ParentAppPublicConfigVO getPublicConfig() {
        ParentAppPublicConfigVO vo = new ParentAppPublicConfigVO();
        vo.setAccessMode(getAccessMode());
        vo.setReviewMode(isReviewMode());
        mergePublicConfigJson(vo);
        vo.setUpdatedAt(OffsetDateTime.now(ZONE_CN).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        return vo;
    }

    @Override
    public ParentAppGuestPreviewVO getGuestPreview() {
        ParentAppGuestPreviewVO vo = new ParentAppGuestPreviewVO();
        vo.setDisclaimer("以下为示例内容，非您家庭真实数据");
        List<ParentAppGuestPreviewVO.PreviewSkill> skills = new ArrayList<>();
        skills.add(previewSkill("preview_1", "睡前故事", "示例技能，登录并绑定设备后可下发至机器人"));
        skills.add(previewSkill("preview_2", "英语跟读", "示例技能，登录后可管理并下发对话技能"));
        vo.setRecommendedSkills(skills);
        return vo;
    }

    @Override
    public String getAccessMode() {
        String v = StringUtils.trimToEmpty(sysParamsService.getValue(PARAM_ACCESS_MODE, true));
        if ("open".equalsIgnoreCase(v)) {
            return "open";
        }
        return DEFAULT_ACCESS_MODE;
    }

    @Override
    public boolean isReviewMode() {
        String v = sysParamsService.getValue(PARAM_REVIEW_MODE, true);
        return "true".equalsIgnoreCase(StringUtils.trimToEmpty(v));
    }

    @Override
    public ParentAppAdminSettingsVO adminGetSettings() {
        ParentAppAdminSettingsVO vo = new ParentAppAdminSettingsVO();
        vo.setAccessMode(getAccessMode());
        vo.setReviewMode(isReviewMode());
        JsonNode root = readPublicConfigRoot();
        vo.setAppName(textOrDefault(root, "appName", "智伴家语"));
        JsonNode banner = root.get("homeBanner");
        if (banner != null && banner.isObject()) {
            vo.setHomeBannerTitle(banner.path("title").asText(""));
            vo.setHomeBannerSubtitle(banner.path("subtitle").asText(""));
            vo.setHomeBannerTags(readStringList(banner.get("tags")));
        }
        vo.setLoginHint(textOrDefault(root, "loginHint", ""));
        vo.setGuestTabHints(readStringMap(root.get("guestTabHints")));
        return vo;
    }

    @Override
    public void adminSaveSettings(ParentAppAdminSettingsSaveDTO dto) {
        String mode = StringUtils.trimToEmpty(dto.getAccessMode()).toLowerCase(Locale.ROOT);
        if (!"open".equals(mode) && !"internal_beta".equals(mode)) {
            throw new RenException("accessMode 仅支持 open 或 internal_beta");
        }
        sysParamsService.updateValueByCode(PARAM_ACCESS_MODE, mode);
        boolean review = Boolean.TRUE.equals(dto.getReviewMode());
        sysParamsService.updateValueByCode(PARAM_REVIEW_MODE, review ? "true" : "false");
    }

    @Override
    public void adminSavePublicConfig(ParentAppAdminPublicConfigSaveDTO dto) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("appName", StringUtils.defaultIfBlank(dto.getAppName(), "智伴家语"));
        ObjectNode banner = objectMapper.createObjectNode();
        banner.put("title", dto.getHomeBannerTitle());
        banner.put("subtitle", dto.getHomeBannerSubtitle());
        if (dto.getHomeBannerTags() != null) {
            banner.set("tags", objectMapper.valueToTree(dto.getHomeBannerTags()));
        }
        root.set("homeBanner", banner);
        if (dto.getLoginHint() != null) {
            root.put("loginHint", dto.getLoginHint());
        }
        if (dto.getGuestTabHints() != null) {
            root.set("guestTabHints", objectMapper.valueToTree(dto.getGuestTabHints()));
        }
        try {
            sysParamsService.updateValueByCode(PARAM_PUBLIC_CONFIG, objectMapper.writeValueAsString(root));
        } catch (Exception e) {
            throw new RenException("保存公开配置失败", e);
        }
    }

    private void mergePublicConfigJson(ParentAppPublicConfigVO vo) {
        JsonNode root = readPublicConfigRoot();
        vo.setAppName(textOrDefault(root, "appName", "智伴家语"));
        JsonNode banner = root.get("homeBanner");
        if (banner != null && banner.isObject()) {
            ParentAppPublicConfigVO.HomeBanner hb = new ParentAppPublicConfigVO.HomeBanner();
            hb.setTitle(banner.path("title").asText("智伴家语 · 家长端"));
            hb.setSubtitle(banner.path("subtitle").asText(""));
            hb.setTags(readStringList(banner.get("tags")));
            vo.setHomeBanner(hb);
        } else {
            ParentAppPublicConfigVO.HomeBanner hb = new ParentAppPublicConfigVO.HomeBanner();
            hb.setTitle("智伴家语 · 家长端");
            hb.setSubtitle("");
            vo.setHomeBanner(hb);
        }
        vo.setLoginHint(textOrDefault(root, "loginHint", null));
        vo.setGuestTabHints(readStringMap(root.get("guestTabHints")));
    }

    private JsonNode readPublicConfigRoot() {
        String json = sysParamsService.getValue(PARAM_PUBLIC_CONFIG, true);
        if (StringUtils.isBlank(json)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.createObjectNode();
        }
    }

    private static String textOrDefault(JsonNode root, String field, String defaultVal) {
        if (root == null || !root.has(field) || root.get(field).isNull()) {
            return defaultVal;
        }
        String t = root.get(field).asText();
        return StringUtils.isNotBlank(t) ? t : defaultVal;
    }

    private List<String> readStringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return null;
        }
        List<String> list = new ArrayList<>();
        node.forEach(n -> list.add(n.asText()));
        return list.isEmpty() ? null : list;
    }

    private Map<String, String> readStringMap(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        Map<String, String> map = new LinkedHashMap<>();
        node.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asText()));
        return map.isEmpty() ? null : map;
    }

    private static ParentAppGuestPreviewVO.PreviewSkill previewSkill(String id, String name, String desc) {
        ParentAppGuestPreviewVO.PreviewSkill s = new ParentAppGuestPreviewVO.PreviewSkill();
        s.setId(id);
        s.setName(name);
        s.setDescription(desc);
        s.setIsPreview(true);
        return s;
    }
}
