package com.guatai.yangaicodemother;

import dev.langchain4j.community.store.embedding.redis.spring.RedisEmbeddingStoreAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {RedisEmbeddingStoreAutoConfiguration.class})
@MapperScan("com.guatai.yangaicodemother.mapper")
public class YangAiCodeMotherApplication {

    public static void main(String[] args) {
        SpringApplication.run(YangAiCodeMotherApplication.class, args);
    }

}
