//
// Source code recreated from a .class file by IntelliJ IDEA
package org.springframework.boot.web.servlet.context;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ScopeMetadataResolver;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class AnnotationConfigServletWebServerApplicationContext extends ServletWebServerApplicationContext implements AnnotationConfigRegistry {
    private final AnnotatedBeanDefinitionReader reader;
    private final ClassPathBeanDefinitionScanner scanner;
    private final Set<Class<?>> annotatedClasses;
    private String[] basePackages;

    public AnnotationConfigServletWebServerApplicationContext() {
        this.annotatedClasses = new LinkedHashSet();
        this.reader = new AnnotatedBeanDefinitionReader(this);
        this.scanner = new ClassPathBeanDefinitionScanner(this);
    }

    public AnnotationConfigServletWebServerApplicationContext(DefaultListableBeanFactory beanFactory) {
        super(beanFactory);
        this.annotatedClasses = new LinkedHashSet();
        this.reader = new AnnotatedBeanDefinitionReader(this);
        this.scanner = new ClassPathBeanDefinitionScanner(this);
    }

    public AnnotationConfigServletWebServerApplicationContext(Class<?>... annotatedClasses) {
        this();
        this.register(annotatedClasses);
        this.refresh();
    }

    public AnnotationConfigServletWebServerApplicationContext(String... basePackages) {
        this();
        this.scan(basePackages);
        this.refresh();
    }

    public void setEnvironment(ConfigurableEnvironment environment) {
        super.setEnvironment(environment);
        this.reader.setEnvironment(environment);
        this.scanner.setEnvironment(environment);
    }

    public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
        this.reader.setBeanNameGenerator(beanNameGenerator);
        this.scanner.setBeanNameGenerator(beanNameGenerator);
        this.getBeanFactory().registerSingleton("org.springframework.context.annotation.internalConfigurationBeanNameGenerator", beanNameGenerator);
    }

    public void setScopeMetadataResolver(ScopeMetadataResolver scopeMetadataResolver) {
        this.reader.setScopeMetadataResolver(scopeMetadataResolver);
        this.scanner.setScopeMetadataResolver(scopeMetadataResolver);
    }

    public final void register(Class<?>... annotatedClasses) {
        Assert.notEmpty(annotatedClasses, "At least one annotated class must be specified");
        this.annotatedClasses.addAll(Arrays.asList(annotatedClasses));
    }

    public final void scan(String... basePackages) {
        Assert.notEmpty(basePackages, "At least one base package must be specified");
        this.basePackages = basePackages;
    }

    protected void prepareRefresh() {
        this.scanner.clearCache();
        super.prepareRefresh();
    }

    protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        super.postProcessBeanFactory(beanFactory);
        if (this.basePackages != null && this.basePackages.length > 0) {
            this.scanner.scan(this.basePackages);
        }

        if (!this.annotatedClasses.isEmpty()) {
            this.reader.register(ClassUtils.toClassArray(this.annotatedClasses));
        }

    }
}
