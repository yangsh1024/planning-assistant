package com.ysh.planning;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/** 应用启动入口，统一装配账本各业务模块。 */
@SpringBootApplication(
        scanBasePackages = "com.ysh.planning",
        exclude = UserDetailsServiceAutoConfiguration.class
)
@MapperScan(value = "com.ysh.planning", annotationClass = Mapper.class)
public class Application {

    /**
     * 启动 Spring Boot 应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
