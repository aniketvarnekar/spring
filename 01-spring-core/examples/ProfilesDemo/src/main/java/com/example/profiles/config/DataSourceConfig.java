/*
 * Declares different DataSourceInfo beans for different profiles.
 *
 * @Profile on a @Bean method limits that bean's registration to the listed profiles.
 * When neither "dev" nor "prod" is active, no DataSourceInfo bean is registered
 * and the application fails to start — this surfaces missing-profile configuration
 * errors at startup rather than at first use.
 */
package com.example.profiles.config;

import com.example.profiles.datasource.DataSourceInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class DataSourceConfig {

    @Bean
    @Profile("dev")
    public DataSourceInfo devDataSource(@Value("${datasource.url}") String url) {
        // The URL comes from application-dev.yaml, which overrides application.yaml.
        return new DataSourceInfo("H2 in-memory", url);
    }

    @Bean
    @Profile("prod")
    public DataSourceInfo prodDataSource(@Value("${datasource.url}") String url) {
        return new DataSourceInfo("PostgreSQL", url);
    }
}
