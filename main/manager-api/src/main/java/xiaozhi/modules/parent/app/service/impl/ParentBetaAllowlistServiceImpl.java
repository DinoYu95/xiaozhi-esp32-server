package xiaozhi.modules.parent.app.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import lombok.RequiredArgsConstructor;
import xiaozhi.common.exception.RenException;
import xiaozhi.common.page.PageData;
import xiaozhi.modules.parent.app.dao.ParentBetaAllowlistDao;
import xiaozhi.modules.parent.app.dto.ParentBetaAllowlistSaveDTO;
import xiaozhi.modules.parent.app.entity.ParentBetaAllowlistEntity;
import xiaozhi.modules.parent.app.service.ParentBetaAllowlistService;
import xiaozhi.modules.parent.app.vo.ParentBetaAllowlistVO;

@Service
@RequiredArgsConstructor
public class ParentBetaAllowlistServiceImpl implements ParentBetaAllowlistService {

    private final ParentBetaAllowlistDao parentBetaAllowlistDao;

    @Override
    public PageData<ParentBetaAllowlistVO> adminPage(Map<String, Object> params) {
        int page = parseInt(params.get("page"), 1);
        int limit = parseInt(params.get("limit"), 20);
        String keyword = StringUtils.trimToNull(stringParam(params.get("keyword")));
        String enabled = stringParam(params.get("enabled"));

        LambdaQueryWrapper<ParentBetaAllowlistEntity> q = new LambdaQueryWrapper<>();
        if (keyword != null) {
            q.and(w -> w.like(ParentBetaAllowlistEntity::getWechatOpenid, keyword)
                    .or().like(ParentBetaAllowlistEntity::getPhone, keyword)
                    .or().like(ParentBetaAllowlistEntity::getRemark, keyword));
        }
        if ("1".equals(enabled)) {
            q.eq(ParentBetaAllowlistEntity::getEnabled, 1);
        } else if ("0".equals(enabled)) {
            q.eq(ParentBetaAllowlistEntity::getEnabled, 0);
        }
        q.orderByDesc(ParentBetaAllowlistEntity::getUpdateTime);

        Page<ParentBetaAllowlistEntity> pg = parentBetaAllowlistDao.selectPage(new Page<>(page, limit), q);
        List<ParentBetaAllowlistVO> list = new ArrayList<>();
        for (ParentBetaAllowlistEntity e : pg.getRecords()) {
            list.add(toVo(e));
        }
        return new PageData<>(list, (int) pg.getTotal());
    }

    @Override
    public void adminSave(ParentBetaAllowlistSaveDTO dto) {
        String openId = StringUtils.trimToEmpty(dto.getWechatOpenid());
        if (StringUtils.isBlank(openId)) {
            throw new RenException("openid 不能为空");
        }
        String channel = StringUtils.defaultIfBlank(StringUtils.trimToEmpty(dto.getChannel()), "mini_program");
        Date now = new Date();
        ParentBetaAllowlistEntity entity;
        if (dto.getId() != null) {
            entity = parentBetaAllowlistDao.selectById(dto.getId());
            if (entity == null) {
                throw new RenException("记录不存在");
            }
        } else {
            entity = parentBetaAllowlistDao.selectOne(
                    new LambdaQueryWrapper<ParentBetaAllowlistEntity>()
                            .eq(ParentBetaAllowlistEntity::getWechatOpenid, openId)
                            .eq(ParentBetaAllowlistEntity::getChannel, channel));
            if (entity == null) {
                entity = new ParentBetaAllowlistEntity();
                entity.setCreateTime(now);
            }
        }
        entity.setWechatOpenid(openId);
        entity.setChannel(channel);
        entity.setPhone(StringUtils.trimToNull(dto.getPhone()));
        entity.setSource(StringUtils.defaultIfBlank(StringUtils.trimToEmpty(dto.getSource()), "manual").toLowerCase(Locale.ROOT));
        entity.setEnabled(Boolean.FALSE.equals(dto.getEnabled()) ? 0 : 1);
        entity.setReviewerTest(Boolean.TRUE.equals(dto.getReviewerTest()) ? 1 : 0);
        entity.setRemark(StringUtils.trimToNull(dto.getRemark()));
        entity.setUpdateTime(now);
        if (entity.getId() == null) {
            parentBetaAllowlistDao.insert(entity);
        } else {
            parentBetaAllowlistDao.updateById(entity);
        }
    }

    @Override
    public void adminDelete(Long id) {
        if (id == null) {
            return;
        }
        parentBetaAllowlistDao.deleteById(id);
    }

    private static ParentBetaAllowlistVO toVo(ParentBetaAllowlistEntity e) {
        ParentBetaAllowlistVO vo = new ParentBetaAllowlistVO();
        vo.setId(e.getId());
        vo.setWechatOpenid(e.getWechatOpenid());
        vo.setChannel(e.getChannel());
        vo.setPhone(e.getPhone());
        vo.setSource(e.getSource());
        vo.setEnabled(e.getEnabled() != null && e.getEnabled() == 1);
        vo.setReviewerTest(e.getReviewerTest() != null && e.getReviewerTest() == 1);
        vo.setRemark(e.getRemark());
        vo.setCreateTime(e.getCreateTime());
        vo.setUpdateTime(e.getUpdateTime());
        return vo;
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

    private static String stringParam(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
