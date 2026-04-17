/*
 * Entry point for the BeanLifecycleDemo application.
 *
 * This demo exists solely to observe the sequence in which Spring invokes lifecycle
 * callbacks on a managed bean. Run the application and inspect the console output —
 * each line is printed by a different phase in the lifecycle, ordered exactly as
 * Spring executes them.
 *
 * Expected output order (see LifecycleDemoBean for the callback implementations):
 *
 *   [INSTANTIATION]   Constructor called
 *   [AWARE]           BeanNameAware.setBeanName      → lifecycleDemoBean
 *   [AWARE]           BeanFactoryAware.setBeanFactory
 *   [AWARE]           ApplicationContextAware.setApplicationContext
 *   [BPP-BEFORE]      BeanPostProcessor.postProcessBeforeInitialization
 *   [POST_CONSTRUCT]  @PostConstruct
 *   [INIT_BEAN]       InitializingBean.afterPropertiesSet
 *   [INIT_METHOD]     Custom init-method via @Bean(initMethod=...)
 *   [BPP-AFTER]       BeanPostProcessor.postProcessAfterInitialization
 *   [READY]           Bean is now in use
 *   [PRE_DESTROY]     @PreDestroy
 *   [DESTROY_BEAN]    DisposableBean.destroy
 *   [DESTROY_METHOD]  Custom destroy-method via @Bean(destroyMethod=...)
 */
package com.example.beanlifecycle;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class BeanLifecycleDemoApplication {

    public static void main(String[] args) {
        // SpringApplication creates the ApplicationContext, initializes all singletons,
        // then returns. The context.close() call below triggers the destruction phase.
        ConfigurableApplicationContext context = SpringApplication.run(BeanLifecycleDemoApplication.class, args);

        // Retrieving the bean here to show it is in a usable state post-initialization.
        LifecycleDemoBean bean = context.getBean(LifecycleDemoBean.class);
        bean.doWork();

        // Closing the context triggers @PreDestroy, DisposableBean.destroy(),
        // and the custom destroy-method in that order.
        context.close();
    }
}
