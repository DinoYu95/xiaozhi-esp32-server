package xiaozhi.modules.parent.service.impl;

import java.util.Date;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import lombok.RequiredArgsConstructor;
import xiaozhi.modules.parent.dao.ParentUserTokenDao;
import xiaozhi.modules.parent.entity.ParentUserTokenEntity;
import xiaozhi.modules.parent.service.ParentUserTokenService;
import xiaozhi.modules.security.oauth2.TokenGenerator;

@Service
@RequiredArgsConstructor
public class ParentUserTokenServiceImpl implements ParentUserTokenService {

    private static final int EXPIRE_SECONDS = 3600 * 24 * 7; // 7 天

    private final ParentUserTokenDao parentUserTokenDao;

    @Override
    public TokenResult createToken(Long parentUserId, String channel) {
        Date now = new Date();
        Date expireTime = new Date(now.getTime() + EXPIRE_SECONDS * 1000L);
        String token = TokenGenerator.generateValue();
        ParentUserTokenEntity entity = new ParentUserTokenEntity();
        entity.setParentUserId(parentUserId);
        entity.setToken(token);
        entity.setExpireTime(expireTime);
        entity.setChannel(channel);
        entity.setCreateTime(now);
        parentUserTokenDao.insert(entity);
        return new TokenResult(token, expireTime);
    }

    @Override
    public void invalidateToken(String token) {
        parentUserTokenDao.delete(new LambdaQueryWrapper<ParentUserTokenEntity>()
                .eq(ParentUserTokenEntity::getToken, token));
    }
}
