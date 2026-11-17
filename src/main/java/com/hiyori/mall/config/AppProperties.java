package com.hiyori.mall.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** application.yml 里 app.* 的配置，集中在一处 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Jwt jwt,
        Wx wx,
        List<String> adminOpenids,
        Upload upload,
        Cors cors) {

    public record Jwt(String secret, long ttlHours) {}

    public record Wx(String appid, String secret, boolean mockLogin) {}

    public record Upload(String dir, String publicBaseUrl) {}

    public record Cors(List<String> allowedOrigins) {}
}
