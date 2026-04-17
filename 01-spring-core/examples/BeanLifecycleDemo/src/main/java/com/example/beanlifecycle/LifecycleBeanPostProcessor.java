/*
 * A custom BeanPostProcessor that logs its invocations so the caller can see
 * exactly where BPP hooks land relative to the bean's own initialization callbacks.
 *
 * postProcessBeforeInitialization fires BEFORE @PostConstruct / afterPropertiesSet.
 * postProcessAfterInitialization fires AFTER all init callbacks — this is where
 * Spring's AOP proxy creator runs to wrap beans in proxies if needed.
 *
 * BeanPostProcessors are themselves beans but are instantiated and registered earlier
 * than regular beans so that they are in place before any regular bean initialization begins.
 */
package com.example.beanlifecycle;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

@Component
public class LifecycleBeanPostProcessor implements BeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof LifecycleDemoBean) {
            // This fires before @PostConstruct — the bean exists but its own
            // initialization callbacks have not yet run.
            System.out.println("[BPP-BEFORE]     BeanPostProcessor.postProcessBeforeInitialization → " + beanName);
        }
        // Returning the bean unmodified; returning a different object here replaces
        // the instance that will be stored in the singleton cache.
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof LifecycleDemoBean) {
            // This fires after all init callbacks. AOP creates proxies here.
            // The object returned from this method is what callers receive from getBean().
            System.out.println("[BPP-AFTER]      BeanPostProcessor.postProcessAfterInitialization → " + beanName);
        }
        return bean;
    }
}
