package xiaozhi.modules.parent.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.modules.agent.Enums.AgentChatHistoryType;
import xiaozhi.modules.agent.dto.AgentChatHistoryDTO;
import xiaozhi.modules.agent.service.AgentChatHistoryService;
import xiaozhi.modules.device.dao.DeviceDao;
import xiaozhi.modules.device.entity.DeviceEntity;
import xiaozhi.modules.parent.dao.DeviceChildDao;
import xiaozhi.modules.parent.dao.ParentDeviceBindingDao;
import xiaozhi.modules.parent.entity.DeviceChildEntity;
import xiaozhi.modules.parent.entity.ParentDeviceBindingEntity;
import xiaozhi.modules.parent.service.DailyBriefService;
import xiaozhi.modules.parent.vo.DailyBriefVO;

/**
 * 主孩子今日简报服务实现
 */
@Service
@RequiredArgsConstructor
public class DailyBriefServiceImpl implements DailyBriefService {

    private static final int HIGHLIGHT_MAX_COUNT = 5;
    /** 亮点摘要单条最大字数（过短会像聊天流水账首句） */
    private static final int HIGHLIGHT_MAX_LENGTH = 42;
    /** 内容打分低于此值的用户句不参与亮点（过滤寒暄与敷衍短句） */
    private static final int HIGHLIGHT_MIN_SCORE = 6;
    private static final int TIME_BANDS = 4;
    private static final ZoneId ZONE_ASIA_SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final Pattern DEVICE_CONTROL = Pattern.compile(
            "设备控制|设备操作|控制设备|设备状态", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEATHER_OR_DATE = Pattern.compile(
            "天气|温度|湿度|降雨|气象|日期|时间|星期|月份|年份", Pattern.CASE_INSENSITIVE);
    /** 明显只是开场寒暄、信息量低（易占据「按时间正序前几条」） */
    private static final Pattern GENERIC_OPENER = Pattern.compile(
            "^(哈喽|哈啰|你好|您好|在吗|在么|hello|hi|hey)[，,。.!！?？…\\s]*$",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SUBSTANTIVE_TOPIC = Pattern.compile(
            "故事|游戏|谜语|为什么|怎么办|最喜欢|不喜欢|害怕|开心|难过|聊天|聊聊|讲讲|猜一猜|来猜|想听|想玩|学校|朋友|同学|老师|恐龙|太空|画画|唱歌|动物|植物|科学|作业|一起|好不好|你知道吗|告诉我");
    private static final Pattern WEAK_DISMISS = Pattern.compile(
            "^啊?不(用|需要)了.*|^算了.*|^没事.*|^不用.*", Pattern.CASE_INSENSITIVE);

    private final DeviceChildDao deviceChildDao;
    private final DeviceDao deviceDao;
    private final ParentDeviceBindingDao parentDeviceBindingDao;
    private final AgentChatHistoryService agentChatHistoryService;

    @Override
    public DailyBriefVO getDailyBrief(Long parentUserId, Long childId) {
        ensureParentCanAccessChild(parentUserId, childId);
        DeviceChildEntity child = deviceChildDao.selectById(childId);
        DeviceEntity device = getDeviceForChild(child);
        if (device == null) {
            return emptyBrief(child);
        }
        String macAddress = StringUtils.isNotBlank(device.getMacAddress())
                ? device.getMacAddress() : device.getId();
        if (StringUtils.isBlank(macAddress) || StringUtils.isBlank(device.getAgentId())) {
            return emptyBrief(child);
        }
        LocalDate today = LocalDate.now(ZONE_ASIA_SHANGHAI);
        Date dateStart = Date.from(today.atStartOfDay(ZONE_ASIA_SHANGHAI).toInstant());
        Date dateEnd = Date.from(today.plusDays(1).atStartOfDay(ZONE_ASIA_SHANGHAI).toInstant());

        List<AgentChatHistoryDTO> records = agentChatHistoryService.getTodayByAgentAndMac(
                device.getAgentId(), macAddress, dateStart, dateEnd);
        return buildBrief(child, today, records);
    }

    private void ensureParentCanAccessChild(Long parentUserId, Long childId) {
        DeviceChildEntity child = deviceChildDao.selectById(childId);
        if (child == null) {
            throw new RenException("孩子不存在");
        }
        String deviceId = child.getDeviceId();
        if (StringUtils.isBlank(deviceId)) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
        String normalized = deviceId.replace(":", "_").toLowerCase();
        ParentDeviceBindingEntity binding = parentDeviceBindingDao.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParentDeviceBindingEntity>()
                        .eq(ParentDeviceBindingEntity::getParentUserId, parentUserId)
                        .and(w -> w.eq(ParentDeviceBindingEntity::getDeviceId, deviceId)
                                .or().eq(ParentDeviceBindingEntity::getDeviceId, normalized)));
        if (binding == null) {
            throw new RenException(ErrorCode.PARENT_DEVICE_NOT_BOUND);
        }
    }

    private DeviceEntity getDeviceForChild(DeviceChildEntity child) {
        if (child == null || StringUtils.isBlank(child.getDeviceId())) {
            return null;
        }
        DeviceEntity device = deviceDao.selectById(child.getDeviceId());
        if (device == null) {
            device = deviceDao.selectByIdOrMacVariant(child.getDeviceId());
        }
        return device;
    }

    private DailyBriefVO emptyBrief(DeviceChildEntity child) {
        String childName = child != null && StringUtils.isNotBlank(child.getName())
                ? child.getName().trim() : "宝宝";
        LocalDate today = LocalDate.now(ZONE_ASIA_SHANGHAI);
        return new DailyBriefVO(
                childName,
                today.format(DateTimeFormatter.ISO_LOCAL_DATE),
                0,
                null,
                null,
                List.of());
    }

    private DailyBriefVO buildBrief(DeviceChildEntity child, LocalDate today,
            List<AgentChatHistoryDTO> records) {
        String childName = child != null && StringUtils.isNotBlank(child.getName())
                ? child.getName().trim() : "宝宝";
        String dateStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (records == null || records.isEmpty()) {
            return new DailyBriefVO(childName, dateStr, 0, null, null, List.of());
        }
        int messageCount = records.size();
        Date firstAt = records.get(0).getCreatedAt();
        Date lastAt = records.get(records.size() - 1).getCreatedAt();
        String firstChatAt = firstAt != null
                ? firstAt.toInstant().atZone(ZONE_ASIA_SHANGHAI).format(TIME_FORMAT) : null;
        String lastChatAt = lastAt != null
                ? lastAt.toInstant().atZone(ZONE_ASIA_SHANGHAI).format(TIME_FORMAT) : null;

        List<String> highlights = extractHighlights(records);
        return new DailyBriefVO(childName, dateStr, messageCount, firstChatAt, lastChatAt, highlights);
    }

    /**
     * 从当日全部用户句中选取亮点：先按信息量打分，再在时间轴上分段各取最优，避免只有「早上前五句寒暄」。
     */
    private List<String> extractHighlights(List<AgentChatHistoryDTO> records) {
        List<HighlightPick> candidates = new ArrayList<>();
        for (AgentChatHistoryDTO dto : records) {
            if (dto.getChatType() == null || dto.getChatType() != AgentChatHistoryType.USER.getValue()) {
                continue;
            }
            String content = extractContent(dto.getContent());
            if (!isMeaningfulForHighlight(content)) {
                continue;
            }
            int score = scoreHighlightContent(content);
            if (score < HIGHLIGHT_MIN_SCORE) {
                continue;
            }
            candidates.add(new HighlightPick(content, dto.getCreatedAt(), score));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        candidates.sort(Comparator.comparing(HighlightPick::createdAt, Comparator.nullsLast(Comparator.naturalOrder())));
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        int n = candidates.size();
        int bands = Math.min(TIME_BANDS, n);
        for (int b = 0; b < bands && ordered.size() < HIGHLIGHT_MAX_COUNT; b++) {
            int start = b * n / bands;
            int end = (b + 1) * n / bands;
            HighlightPick best = null;
            for (int i = start; i < end; i++) {
                HighlightPick c = candidates.get(i);
                if (best == null || c.score > best.score) {
                    best = c;
                }
            }
            if (best != null) {
                ordered.add(truncateHighlight(best.content));
            }
        }
        candidates.sort(Comparator.comparingInt((HighlightPick c) -> c.score).reversed());
        for (HighlightPick c : candidates) {
            if (ordered.size() >= HIGHLIGHT_MAX_COUNT) {
                break;
            }
            ordered.add(truncateHighlight(c.content));
        }
        List<String> out = new ArrayList<>(ordered);
        int cap = Math.min(HIGHLIGHT_MAX_COUNT, out.size());
        return cap == out.size() ? out : new ArrayList<>(out.subList(0, cap));
    }

    private static String truncateHighlight(String content) {
        String t = content.trim();
        if (t.length() <= HIGHLIGHT_MAX_LENGTH) {
            return t;
        }
        return t.substring(0, HIGHLIGHT_MAX_LENGTH) + "…";
    }

    /**
     * 信息量打分：偏长句、含话题词加分；纯寒暄、敷衍短拒答减分。
     */
    private int scoreHighlightContent(String content) {
        String t = content.trim();
        int len = t.length();
        int s = Math.min(40, len);
        if (GENERIC_OPENER.matcher(t).matches()) {
            s -= 28;
        }
        if (len <= 20 && t.matches("(?i).*(hello|hi|hey).*") && t.matches(".*你好.*")) {
            s -= 22;
        }
        if (SUBSTANTIVE_TOPIC.matcher(t).find()) {
            s += 22;
        }
        if (WEAK_DISMISS.matcher(t).find()) {
            s -= 18;
        }
        if (len >= 15) {
            s += 8;
        }
        return s;
    }

    private static final class HighlightPick {
        final String content;
        final Date createdAt;
        final int score;

        HighlightPick(String content, Date createdAt, int score) {
            this.content = Objects.requireNonNullElse(content, "");
            this.createdAt = createdAt;
            this.score = score;
        }
    }

    private String extractContent(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "";
        }
        try {
            Map<String, Object> map = JsonUtils.parseObject(raw, Map.class);
            if (map != null && map.containsKey("content")) {
                Object c = map.get("content");
                return c != null ? c.toString().trim() : raw.trim();
            }
        } catch (Exception ignored) {
            // not JSON
        }
        return raw.trim();
    }

    private boolean isMeaningfulForHighlight(String content) {
        if (StringUtils.isBlank(content) || content.length() < 5) {
            return false;
        }
        if (DEVICE_CONTROL.matcher(content).find()) {
            return false;
        }
        if (WEATHER_OR_DATE.matcher(content).find()) {
            return false;
        }
        return true;
    }
}
