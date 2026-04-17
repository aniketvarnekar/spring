/*
 * Demonstrates every standard Spring bean lifecycle callback.
 *
 * This class implements all Aware interfaces and both init/destroy contracts
 * (InitializingBean + DisposableBean), and uses @PostConstruct / @PreDestroy.
 * The custom init-method and destroy-method are declared in BeanConfig.
 *
 * The ordering of callbacks is deterministic within a lifecycle phase but the
 * BeanPostProcessor callbacks interleave with them — see LifecycleBeanPostProcessor
 * to observe where BPP hooks land relative to the bean's own callbacks.
 */
package com.example.beanlifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

public class LifecycleDemoBean
        implements BeanNameAware, BeanFactoryAware, ApplicationContextAware,
                   InitializingBean, DisposableBean {

    public LifecycleDemoBean() {
        System.out.println("[INSTANTIATION]  Constructor called");
    }

    // --- Aware callbacks (invoked in this order by AbstractAutowireCapableBeanFactory) ---

    @Override
    public void setBeanName(String name) {
        // BeanNameAware lets a bean discover its own registered name.
        // Useful for logging and diagnostic tooling; rarely needed in application code.
        System.out.println("[AWARE]          BeanNameAware.setBeanName → " + name);
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        // BeanFactoryAware provides access to the owning BeanFactory.
        // Prefer constructor injection for dependencies; use this only in framework code.
        System.out.println("[AWARE]          BeanFactoryAware.setBeanFactory");
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        // ApplicationContextAware provides access to the full ApplicationContext.
        // The container calls this after all Aware callbacks and before BeanPostProcessors.
        System.out.println("[AWARE]          ApplicationContextAware.setApplicationContext");
    }

    // --- Initialization callbacks ---

    @PostConstruct
    public void postConstruct() {
        // @PostConstruct is processed by CommonAnnotationBeanPostProcessor during
        // postProcessBeforeInitialization, which means it runs BEFORE afterPropertiesSet.
        // All @Autowired dependencies are available here because injection
        // (property population) completed before any of the Aware or init callbacks.
        System.out.println("[POST_CONSTRUCT] @PostConstruct");
    }

    @Override
    public void afterPropertiesSet() {
        // Runs after @PostConstruct. Couples this class to the Spring API.
        // Prefer @PostConstruct or a custom init-method for application code.
        System.out.println("[INIT_BEAN]      InitializingBean.afterPropertiesSet");
    }

    // Declared as init-method in BeanConfig — no Spring API coupling in the method itself.
    public void customInit() {
        System.out.println("[INIT_METHOD]    Custom init-method (declared via @Bean(initMethod=...))");
    }

    // --- Normal usage ---

    public void doWork() {
        System.out.println("[READY]          Bean is in use");
    }

    // --- Destruction callbacks ---

    @PreDestroy
    public void preDestroy() {
        // @PreDestroy is also processed by CommonAnnotationBeanPostProcessor,
        // during the destruction phase. It runs before DisposableBean.destroy().
        // Prototype-scoped beans never reach this method — only singletons do.
        System.out.println("[PRE_DESTROY]    @PreDestroy");
    }

    @Override
    public void destroy() {
        System.out.println("[DESTROY_BEAN]   DisposableBean.destroy");
    }

    // Declared as destroy-method in BeanConfig.
    public void customDestroy() {
        System.out.println("[DESTROY_METHOD] Custom destroy-method (declared via @Bean(destroyMethod=...))");
    }
}
