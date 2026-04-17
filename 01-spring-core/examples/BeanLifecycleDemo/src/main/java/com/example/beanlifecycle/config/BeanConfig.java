/*
 * Declares LifecycleDemoBean as an explicit @Bean so that initMethod and destroyMethod
 * can be specified without modifying LifecycleDemoBean itself.
 *
 * Using @Bean here rather than @Component on LifecycleDemoBean demonstrates that
 * the lifecycle sequence is identical regardless of how the bean is registered —
 * the container applies the same callback chain in both cases.
 */
package com.example.beanlifecycle.config;

import com.example.beanlifecycle.LifecycleDemoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BeanConfig {

    @Bean(initMethod = "customInit", destroyMethod = "customDestroy")
    public LifecycleDemoBean lifecycleDemoBean() {
        // The container calls the constructor here, then applies all callbacks.
        // initMethod fires after InitializingBean.afterPropertiesSet();
        // destroyMethod fires after DisposableBean.destroy().
        return new LifecycleDemoBean();
    }
}
