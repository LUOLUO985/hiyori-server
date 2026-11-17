package com.hiyori.mall;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan("com.hiyori.mall.**.mapper")
public class HiyoriMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(HiyoriMallApplication.class, args);
    }
}
