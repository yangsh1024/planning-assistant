package com.ysh.planning;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(
        scanBasePackages = "com.ysh.planning",
        exclude = UserDetailsServiceAutoConfiguration.class
)
@MapperScan("com.ysh.planning")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
