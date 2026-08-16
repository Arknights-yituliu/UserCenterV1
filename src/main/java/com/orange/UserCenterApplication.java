package com.orange;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * User Center 多站点统一用户服务启动类
 *
 * @author UserCenter
 */
@SpringBootApplication
public class UserCenterApplication {

    /**
     * 程序入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(UserCenterApplication.class, args);
    }
}
