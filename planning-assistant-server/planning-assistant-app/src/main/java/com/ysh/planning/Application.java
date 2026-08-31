package com.ysh.planning;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = "com.ysh.planning",
        exclude = UserDetailsServiceAutoConfiguration.class
)
/** 应用启动入口，统一装配账本各业务模块。 */
@MapperScan("com.ysh.planning")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
