package xiaozhi.modules.agent.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cn.hutool.core.collection.ListUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import xiaozhi.common.constant.Constant;
import xiaozhi.common.page.PageData;
import xiaozhi.common.utils.ConvertUtils;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.common.utils.ToolUtil;
import xiaozhi.modules.agent.Enums.AgentChatHistoryType;
import xiaozhi.modules.agent.dao.AiAgentChatHistoryDao;
import xiaozhi.modules.agent.dto.AgentChatHistoryDTO;
import xiaozhi.modules.agent.dto.AgentChatSessionDTO;
import xiaozhi.modules.agent.entity.AgentChatHistoryEntity;
import xiaozhi.modules.agent.service.AgentChatHistoryService;
import xiaozhi.modules.agent.vo.AgentChatHistoryUserVO;

/**
 * 智能体聊天记录表处理service {@link AgentChatHistoryService} impl
 *
 * @author Goody
 * @version 1.0, 2025/4/30
 * @since 1.0.0
 */
@Service
public class AgentChatHistoryServiceImpl extends ServiceImpl<AiAgentChatHistoryDao, AgentChatHistoryEntity>
        implements AgentChatHistoryService {

    private static final ZoneId ZONE_SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter HISTORY_TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Override
    public PageData<AgentChatSessionDTO> getSessionListByAgentId(Map<String, Object> params) {
        String agentId = (String) params.get("agentId");
        int page = Integer.parseInt(params.get(Constant.PAGE).toString());
        int limit = Integer.parseInt(params.get(Constant.LIMIT).toString());

        // 构建查询条件
        QueryWrapper<AgentChatHistoryEntity> wrapper = new QueryWrapper<>();
        wrapper.select("session_id", "MAX(created_at) as created_at", "COUNT(*) as chat_count")
                .eq("agent_id", agentId)
                .groupBy("session_id")
                .orderByDesc("created_at");

        // 执行分页查询
        Page<Map<String, Object>> pageParam = new Page<>(page, limit);
        IPage<Map<String, Object>> result = this.baseMapper.selectMapsPage(pageParam, wrapper);

        List<AgentChatSessionDTO> records = result.getRecords().stream().map(map -> {
            AgentChatSessionDTO dto = new AgentChatSessionDTO();
            dto.setSessionId((String) map.get("session_id"));
            dto.setCreatedAt((LocalDateTime) map.get("created_at"));
            dto.setChatCount(((Number) map.get("chat_count")).intValue());
            return dto;
        }).collect(Collectors.toList());

        return new PageData<>(records, result.getTotal());
    }

    @Override
    public List<AgentChatHistoryDTO> getChatHistoryBySessionId(String agentId, String sessionId) {
        // 构建查询条件
        QueryWrapper<AgentChatHistoryEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("agent_id", agentId)
                .eq("session_id", sessionId)
                .orderByAsc("created_at");

        // 查询聊天记录
        List<AgentChatHistoryEntity> historyList = list(wrapper);

        // 转换为DTO
        return ConvertUtils.sourceToTarget(historyList, AgentChatHistoryDTO.class);
    }

    @Override
    public List<AgentChatHistoryDTO> getRecentByAgentAndMac(String agentId, String macAddress, int limit) {
        if (org.apache.commons.lang3.StringUtils.isBlank(agentId) || org.apache.commons.lang3.StringUtils.isBlank(macAddress)) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        // 使用 format 兼容查询，支持 B6:C8:35:D6:10:48 与 b6_c8_35_d6_10_48 等格式
        List<AgentChatHistoryEntity> list = baseMapper.selectRecentByAgentAndMacVariants(agentId, macAddress.trim(), safeLimit);
        // 按时间正序返回（便于阅读：从早到晚）
        List<AgentChatHistoryEntity> reversed = new java.util.ArrayList<>(list);
        java.util.Collections.reverse(reversed);
        return ConvertUtils.sourceToTarget(reversed, AgentChatHistoryDTO.class);
    }

    @Override
    public String getFormattedRecentByAgentAndMac(String agentId, String macAddress, int limit, String childDisplayName) {
        String childName = org.apache.commons.lang3.StringUtils.isNotBlank(childDisplayName) ? childDisplayName : "孩子";
        LocalDate today = LocalDate.now(ZONE_SHANGHAI);
        Date dateStart = Date.from(today.atStartOfDay(ZONE_SHANGHAI).toInstant());
        Date dateEnd = Date.from(today.plusDays(1).atStartOfDay(ZONE_SHANGHAI).toInstant());
        List<AgentChatHistoryDTO> todayList = getTodayByAgentAndMac(agentId, macAddress, dateStart, dateEnd);
        List<AgentChatHistoryDTO> recentList = getRecentByAgentAndMac(agentId, macAddress, limit);
        if ((todayList == null || todayList.isEmpty()) && (recentList == null || recentList.isEmpty())) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("【时区】Asia/Shanghai\n");
        int todayCount = todayList != null ? todayList.size() : 0;
        sb.append("【今日 ").append(today).append(" 设备端对话】共 ").append(todayCount).append(" 条消息。\n");
        if (todayCount > 0) {
            for (AgentChatHistoryDTO dto : todayList) {
                appendFormattedHistoryLine(sb, dto, childName);
            }
        } else {
            sb.append("（今日尚无消息入库。家长问「今天聊了吗」应明确说今天还没有；下方「更早对话」仅作背景，")
                    .append("不得称为今天说过的话，也不得说「我刚调取了今天的聊天记录」却引用更早内容。）\n");
        }
        List<AgentChatHistoryDTO> earlier = new ArrayList<>();
        if (recentList != null) {
            for (AgentChatHistoryDTO dto : recentList) {
                if (dto.getCreatedAt() == null) {
                    earlier.add(dto);
                    continue;
                }
                LocalDate d = dto.getCreatedAt().toInstant().atZone(ZONE_SHANGHAI).toLocalDate();
                if (!today.equals(d)) {
                    earlier.add(dto);
                }
            }
        }
        if (!earlier.isEmpty()) {
            sb.append("\n【更早对话（背景参考，勿说成「今天」）】\n");
            for (AgentChatHistoryDTO dto : earlier) {
                appendFormattedHistoryLine(sb, dto, childName);
            }
        }
        return sb.toString().trim();
    }

    private void appendFormattedHistoryLine(StringBuilder sb, AgentChatHistoryDTO dto, String childName) {
        String lineContent = extractContentFromString(dto.getContent());
        if (lineContent == null || lineContent.isBlank()) {
            return;
        }
        String role = (dto.getChatType() != null && dto.getChatType() == AgentChatHistoryType.AGENT.getValue())
                ? "助手" : childName;
        String ts = "";
        if (dto.getCreatedAt() != null) {
            ts = dto.getCreatedAt().toInstant().atZone(ZONE_SHANGHAI).format(HISTORY_TIME_FMT);
        }
        if (org.apache.commons.lang3.StringUtils.isNotBlank(ts)) {
            sb.append("[").append(ts).append("] ");
        }
        sb.append(role).append("：").append(lineContent.trim()).append("\n");
    }

    @Override
    public List<AgentChatHistoryDTO> getTodayByAgentAndMac(String agentId, String macAddress, java.util.Date dateStart, java.util.Date dateEnd) {
        if (org.apache.commons.lang3.StringUtils.isBlank(agentId) || org.apache.commons.lang3.StringUtils.isBlank(macAddress)
                || dateStart == null || dateEnd == null) {
            return List.of();
        }
        List<AgentChatHistoryEntity> list = baseMapper.selectTodayByAgentAndMacVariants(agentId, macAddress.trim(), dateStart, dateEnd);
        return ConvertUtils.sourceToTarget(list, AgentChatHistoryDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByAgentId(String agentId, Boolean deleteAudio, Boolean deleteText) {
        if (deleteAudio) {
            // 分批删除音频,避免超时
            List<String> audioIds = baseMapper.getAudioIdsByAgentId(agentId);
            if (ToolUtil.isNotEmpty(audioIds)) {
                // 每批删除1000条
                List<List<String>> batch = ListUtil.split(audioIds, 1000);
                batch.forEach(dataList->{
                    baseMapper.deleteAudioByIds(dataList);
                });
            }
        }
        if (deleteAudio && !deleteText) {
            baseMapper.deleteAudioIdByAgentId(agentId);
        }
        if (deleteText) {
            baseMapper.deleteHistoryByAgentId(agentId);
        }

    }

    @Override
    public List<AgentChatHistoryUserVO> getRecentlyFiftyByAgentId(String agentId) {
        // 构建查询条件(不添加按照创建时间排序，数据本来就是主键越大创建时间越大
        // 不添加这样可以减少排序全部数据在分页的全盘扫描消耗)
        LambdaQueryWrapper<AgentChatHistoryEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.select(AgentChatHistoryEntity::getContent, AgentChatHistoryEntity::getAudioId)
                .eq(AgentChatHistoryEntity::getAgentId, agentId)
                .eq(AgentChatHistoryEntity::getChatType, AgentChatHistoryType.USER.getValue())
                .isNotNull(AgentChatHistoryEntity::getAudioId)
                // 添加此行，确保查询结果按照创建时间降序排列
                // 使用id的原因：数据形式，id越大的创建时间就越晚，所以使用id的结果和创建时间降序排列结果一样
                // id作为降序排列的优势，性能高，有主键索引，不用在排序的时候重新进行排除扫描比较
                .orderByDesc(AgentChatHistoryEntity::getId);

        // 构建分页查询，查询前50页数据
        Page<AgentChatHistoryEntity> pageParam = new Page<>(0, 50);
        IPage<AgentChatHistoryEntity> result = this.baseMapper.selectPage(pageParam, wrapper);
        return result.getRecords().stream().map(item -> {
            AgentChatHistoryUserVO vo = ConvertUtils.sourceToTarget(item, AgentChatHistoryUserVO.class);
            // 处理 content 字段，确保只返回聊天内容
            if (vo != null && vo.getContent() != null) {
                vo.setContent(extractContentFromString(vo.getContent()));
            }
            return vo;
        }).toList();
    }

    /**
     * 从 content 字段中提取聊天内容
     * 如果 content 是 JSON 格式（如 {"speaker": "未知说话人", "content": "现在几点了。"}），则提取 content
     * 字段
     * 如果 content 是普通字符串，则直接返回
     * 
     * @param content 原始内容
     * @return 提取的聊天内容
     */
    private String extractContentFromString(String content) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }

        // 尝试解析为 JSON
        try {
            Map<String, Object> jsonMap = JsonUtils.parseObject(content, Map.class);
            if (jsonMap != null && jsonMap.containsKey("content")) {
                Object contentObj = jsonMap.get("content");
                return contentObj != null ? contentObj.toString() : content;
            }
        } catch (Exception e) {
            // 如果不是有效的 JSON，直接返回原内容
        }

        // 如果不是 JSON 格式或没有 content 字段，直接返回原内容
        return content;
    }

    @Override
    public String getContentByAudioId(String audioId) {
        AgentChatHistoryEntity agentChatHistoryEntity = baseMapper
                .selectOne(new LambdaQueryWrapper<AgentChatHistoryEntity>()
                        .select(AgentChatHistoryEntity::getContent)
                        .eq(AgentChatHistoryEntity::getAudioId, audioId));
        return agentChatHistoryEntity == null ? null : agentChatHistoryEntity.getContent();
    }

    @Override
    public boolean isAudioOwnedByAgent(String audioId, String agentId) {
        // 查询是否有指定音频id和智能体id的数据，如果有且只有一条说明此数据属性此智能体
        Long row = baseMapper.selectCount(new LambdaQueryWrapper<AgentChatHistoryEntity>()
                .eq(AgentChatHistoryEntity::getAudioId, audioId)
                .eq(AgentChatHistoryEntity::getAgentId, agentId));
        return row == 1;
    }

}
