package com.guatai.yangaicodemother;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.guatai.yangaicodemother.mapper")
public class YangAiCodeMotherApplication {

    public static void main(String[] args) {
        SpringApplication.run(YangAiCodeMotherApplication.class, args);
    }

}
