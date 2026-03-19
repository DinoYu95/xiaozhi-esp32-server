package xiaozhi.modules.parent.service.impl;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
    private static final int HIGHLIGHT_MAX_LENGTH = 20;
    private static final ZoneId ZONE_ASIA_SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private static final Pattern DEVICE_CONTROL = Pattern.compile(
            "设备控制|设备操作|控制设备|设备状态", Pattern.CASE_INSENSITIVE);
    private static final Pattern WEATHER_OR_DATE = Pattern.compile(
            "天气|温度|湿度|降雨|气象|日期|时间|星期|月份|年份", Pattern.CASE_INSENSITIVE);

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

    private List<String> extractHighlights(List<AgentChatHistoryDTO> records) {
        List<String> result = new ArrayList<>();
        for (AgentChatHistoryDTO dto : records) {
            if (dto.getChatType() == null || dto.getChatType() != AgentChatHistoryType.USER.getValue()) {
                continue;
            }
            String content = extractContent(dto.getContent());
            if (!isMeaningfulForHighlight(content)) {
                continue;
            }
            String truncated = content.length() > HIGHLIGHT_MAX_LENGTH
                    ? content.substring(0, HIGHLIGHT_MAX_LENGTH) + "…" : content;
            if (!result.contains(truncated)) {
                result.add(truncated);
            }
            if (result.size() >= HIGHLIGHT_MAX_COUNT) {
                break;
            }
        }
        return result;
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
