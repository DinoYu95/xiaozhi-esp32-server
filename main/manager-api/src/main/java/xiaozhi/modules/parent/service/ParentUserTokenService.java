package xiaozhi.modules.parent.service;

import java.util.Date;

/**
 * 家长端 token 服务
 */
public interface ParentUserTokenService {

    /**
     * 生成 token 并入库，返回 token 与过期时间
     */
    TokenResult createToken(Long parentUserId, String channel);

    /**
     * 使 token 失效（删除记录）
     */
    void invalidateToken(String token);

    /**
     * token 与过期时间
     */
    record TokenResult(String token, Date expireTime) {
    }
}
