/*
 * Custom auto-configuration for the GreetingService.
 *
 * This class demonstrates the standard auto-configuration pattern:
 *
 *   @AutoConfiguration          — marks this as an auto-configuration class;
 *                                 equivalent to @Configuration(proxyBeanMethods=false)
 *                                 plus auto-config ordering support.
 *
 *   @ConditionalOnMissingBean   — the entire configuration backs off if the user
 *                                 declares any bean of type GreetingService.
 *                                 This is the primary backoff mechanism.
 *
 *   @EnableConfigurationProperties — registers GreetingProperties for binding
 *                                    so it is available as a @Bean parameter.
 *
 * To register this class for auto-configuration, it must be listed in:
 *   src/main/resources/META-INF/spring/
 *     org.springframework.boot.autoconfigure.AutoConfiguration.imports
 *
 * In this demo, the auto-configuration is in the same project as the application,
 * so component scanning would also find it. In a real library, the auto-configuration
 * jar should NOT be on the component scan path — only in the .imports file.
 */
package com.example.autoconfiguration.autoconfigure;

import com.example.autoconfiguration.greeting.DefaultGreetingService;
import com.example.autoconfiguration.greeting.GreetingProperties;
import com.example.autoconfiguration.greeting.GreetingService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnMissingBean(GreetingService.class)
@EnableConfigurationProperties(GreetingProperties.class)
public class GreetingAutoConfiguration {

    @Bean
    // @ConditionalOnMissingBean at the class level covers the whole configuration,
    // but repeating it at the method level is common practice in Spring Boot's own
    // auto-configurations for clarity and robustness.
    @ConditionalOnMissingBean
    public GreetingService greetingService(GreetingProperties properties) {
        return new DefaultGreetingService(properties.getMessage());
    }
}
