package xiaozhi.modules.parent.filter;

import java.io.IOException;
import java.util.Date;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import xiaozhi.common.constant.Constant;
import xiaozhi.common.exception.ErrorCode;
import xiaozhi.common.utils.HttpContextUtils;
import xiaozhi.common.utils.JsonUtils;
import xiaozhi.common.utils.Result;
import xiaozhi.modules.parent.dao.ParentUserTokenDao;
import xiaozhi.modules.parent.entity.ParentUserTokenEntity;

/**
 * 家长端 token 校验：从 Header 取 token，查 parent_user_token，写入 request 属性 parent_user_id。
 */
@Component
@RequiredArgsConstructor
public class ParentTokenFilter extends jakarta.servlet.http.HttpFilter {

    private final ParentUserTokenDao parentUserTokenDao;

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        String uri = request.getRequestURI();
        // 只拦截 parent-api，其它路径一律放行
        if (uri == null || (!uri.contains("/parent-api/"))) {
            chain.doFilter(request, response);
            return;
        }

        String token = getRequestToken(request);
        if (StringUtils.isBlank(token)) {
            writeUnauthorized(response);
            return;
        }
        ParentUserTokenEntity tokenEntity = parentUserTokenDao.selectOne(
                new LambdaQueryWrapper<ParentUserTokenEntity>()
                        .eq(ParentUserTokenEntity::getToken, token));
        if (tokenEntity == null || tokenEntity.getExpireTime() == null
                || tokenEntity.getExpireTime().before(new Date())) {
            writeUnauthorized(response);
            return;
        }
        request.setAttribute(Constant.PARENT_USER_KEY, tokenEntity.getParentUserId());
        chain.doFilter(request, response);
    }

    private static String getRequestToken(HttpServletRequest request) {
        String authorization = request.getHeader(Constant.AUTHORIZATION);
        if (StringUtils.isNotBlank(authorization) && authorization.startsWith("Bearer ")) {
            return authorization.substring(7);
        }
        return null;
    }

    private static void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=utf-8");
        response.setHeader("Access-Control-Allow-Credentials", "true");
        response.setHeader("Access-Control-Allow-Origin", HttpContextUtils.getOrigin());
        response.getWriter().print(JsonUtils.toJsonString(new Result<Void>().error(ErrorCode.UNAUTHORIZED)));
    }
}
