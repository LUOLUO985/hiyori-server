package com.hiyori.mall.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiyori.mall.common.BizException;
import com.hiyori.mall.common.ErrorCode;
import com.hiyori.mall.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 调微信 code2Session：小程序 wx.login() 拿到的 code → openid。
 * 文档：https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/user-login/code2Session.html
 * 注意 session_key 只在服务端用，绝不能下发给小程序。
 */
@Component
public class WxClient {

    private static final Logger log = LoggerFactory.getLogger(WxClient.class);

    private final AppProperties.Wx wx;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public WxClient(AppProperties props, ObjectMapper objectMapper) {
        this.wx = props.wx();
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        this.restClient = RestClient.builder()
                .baseUrl("https://api.weixin.qq.com")
                .requestFactory(factory)
                .build();
    }

    public String code2Openid(String code) {
        if (wx.mockLogin()) {
            // 本地开发：不连微信，code 直接当 openid（测试里用 dev-admin 模拟老板登录）
            return code;
        }
        if (wx.appid() == null || wx.appid().isBlank() || wx.secret() == null || wx.secret().isBlank()) {
            throw new BizException(ErrorCode.WX_LOGIN_FAILED, "服务端未配置 WX_APPID / WX_SECRET");
        }
        try {
            // 微信这个接口返回的 Content-Type 是 text/plain，先拿字符串再自己解析
            String body = restClient.get()
                    .uri(b -> b.path("/sns/jscode2session")
                            .queryParam("appid", wx.appid())
                            .queryParam("secret", wx.secret())
                            .queryParam("js_code", code)
                            .queryParam("grant_type", "authorization_code")
                            .build())
                    .retrieve()
                    .body(String.class);
            JsonNode json = objectMapper.readTree(body);
            if (json.hasNonNull("errcode") && json.get("errcode").asInt() != 0) {
                log.warn("code2Session 失败 errcode={} errmsg={}", json.get("errcode"), json.path("errmsg").asText());
                throw new BizException(ErrorCode.WX_LOGIN_FAILED);
            }
            String openid = json.path("openid").asText("");
            if (openid.isEmpty()) {
                throw new BizException(ErrorCode.WX_LOGIN_FAILED);
            }
            return openid;
        } catch (RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.warn("调用微信 code2Session 出错", e);
            throw new BizException(ErrorCode.WX_LOGIN_FAILED);
        }
    }
}
