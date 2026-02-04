package com.old.silence.job.server.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Repository Auto Configuration
 * Automatically scans and registers MyBatis Mapper interfaces
 */
@AutoConfiguration
@MapperScan("com.old.silence.job.server.infrastructure.persistence.dao")
public class RepositoryAutoConfiguration {

}
