package com.hiyori.mall.auth;

import com.hiyori.mall.auth.entity.AppUser;
import com.hiyori.mall.common.BizException;
import com.hiyori.mall.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 鉴权拦截器：
 *   /api/me/**    需要登录
 *   /api/admin/** 需要登录 + 管理员（角色从数据库实时读取）
 */
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER = "currentUser";
    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;
    private final AuthService authService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true; // CORS 预检
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        long userId = jwtService.parseUserId(header.substring(BEARER.length()).trim());
        AppUser user = authService.requireUser(userId);

        if (request.getRequestURI().startsWith("/api/admin/") && !user.isAdmin()) {
            throw new BizException(ErrorCode.FORBIDDEN);
        }
        request.setAttribute(ATTR_USER, user);
        return true;
    }
}
