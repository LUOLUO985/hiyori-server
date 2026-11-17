package com.hiyori.mall.auth;

import com.hiyori.mall.auth.AuthService.LoginResult;
import com.hiyori.mall.auth.AuthService.UserVO;
import com.hiyori.mall.auth.entity.AppUser;
import com.hiyori.mall.common.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    public record LoginRequest(@NotBlank @Size(max = 128) String code) {}

    /** 小程序：wx.login() → 把 code 发到这里 → 拿到 token */
    @PostMapping("/api/auth/login")
    public Result<LoginResult> login(@Valid @RequestBody LoginRequest req) {
        return Result.ok(authService.login(req.code()));
    }

    @GetMapping("/api/me")
    public Result<UserVO> me(@RequestAttribute(AuthInterceptor.ATTR_USER) AppUser user) {
        return Result.ok(authService.profile(user.getId()));
    }
}
