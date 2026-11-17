package com.hiyori.mall.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hiyori.mall.auth.entity.AppUser;
import com.hiyori.mall.auth.mapper.AppUserMapper;
import com.hiyori.mall.common.BizException;
import com.hiyori.mall.common.ErrorCode;
import com.hiyori.mall.config.AppProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final WxClient wxClient;
    private final JwtService jwtService;
    private final AppUserMapper userMapper;
    private final Set<String> adminOpenids;

    public AuthService(WxClient wxClient, JwtService jwtService, AppUserMapper userMapper, AppProperties props) {
        this.wxClient = wxClient;
        this.jwtService = jwtService;
        this.userMapper = userMapper;
        List<String> admins = props.adminOpenids() == null ? List.of() : props.adminOpenids();
        this.adminOpenids = admins.stream().map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }

    public record LoginResult(String token, long expiresIn, UserVO user) {}

    public record UserVO(long id, String nickname, String avatar, String role) {
        static UserVO of(AppUser u) {
            return new UserVO(u.getId(), u.getNickname(), u.getAvatar(), u.getRole());
        }
    }

    /** 首次登录自动注册；openid 在管理员名单里的自动升级为 ADMIN */
    public LoginResult login(String code) {
        String openid = wxClient.code2Openid(code);
        AppUser user = findOrCreate(openid);

        String role = adminOpenids.contains(openid) ? AppUser.ROLE_ADMIN : user.getRole();
        user.setRole(role);
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        return new LoginResult(jwtService.issue(user.getId()), jwtService.expiresInSeconds(), UserVO.of(user));
    }

    public AppUser requireUser(long userId) {
        AppUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }

    public UserVO profile(long userId) {
        return UserVO.of(requireUser(userId));
    }

    private AppUser findOrCreate(String openid) {
        AppUser user = selectByOpenid(openid);
        if (user != null) {
            return user;
        }
        AppUser created = new AppUser();
        created.setOpenid(openid);
        created.setNickname("Hiyori 的老朋友");
        created.setAvatar("");
        created.setRole(AppUser.ROLE_USER);
        try {
            userMapper.insert(created);
            return created;
        } catch (DuplicateKeyException e) {
            // 同一个人连点两次登录：并发插入撞了唯一索引，直接读回已有那条
            return selectByOpenid(openid);
        }
    }

    private AppUser selectByOpenid(String openid) {
        return userMapper.selectOne(new LambdaQueryWrapper<AppUser>().eq(AppUser::getOpenid, openid));
    }
}
