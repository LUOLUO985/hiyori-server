package com.hiyori.mall.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * 先用 Spring 默认的进程内缓存（ConcurrentMap）。
 * 单机部署够用；以后多实例部署，加上 spring-boot-starter-data-redis 并设置
 * spring.cache.type=redis 即可切到 Redis，业务代码里的 @Cacheable / @CacheEvict 不用改。
 */
@Configuration
@EnableCaching
public class CacheConfig {}
