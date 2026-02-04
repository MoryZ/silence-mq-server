package com.old.silence.job.server.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mapper Auto Configuration
 */
@Configuration
@MapperScan("com.old.silence.job.server.infrastructure.persistence.dao")
public class MyBatisMapperConfiguration {

}
