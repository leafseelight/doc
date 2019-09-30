//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.springframework.boot.context.config;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.beans.BeanUtils;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/*
 * 代理应用上下文初始化器
 */

public class DelegatingApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {
    private static final String PROPERTY_NAME = "context.initializer.classes";
    private int order = 0;  // 创建bean的优先级顺序 数字越小 优先级越高

    public DelegatingApplicationContextInitializer() {
    }

	// applicationContext的environment和profiles设置完成后调用
    public void initialize(ConfigurableApplicationContext context) {
        ConfigurableEnvironment environment = context.getEnvironment();
        List<Class<?>> initializerClasses = this.getInitializerClasses(environment);
        if (!initializerClasses.isEmpty()) {
            this.applyInitializerClasses(context, initializerClasses);
        }

    }

    private List<Class<?>> getInitializerClasses(ConfigurableEnvironment env) {
        String classNames = env.getProperty("context.initializer.classes");
        List<Class<?>> classes = new ArrayList();
        if (StringUtils.hasLength(classNames)) {
            String[] var4 = StringUtils.tokenizeToStringArray(classNames, ",");
            int var5 = var4.length;

            for(int var6 = 0; var6 < var5; ++var6) {
                String className = var4[var6];
                classes.add(this.getInitializerClass(className));
            }
        }

        return classes;
    }

    private Class<?> getInitializerClass(String className) throws LinkageError {
        try {
            Class<?> initializerClass = ClassUtils.forName(className, ClassUtils.getDefaultClassLoader());
            Assert.isAssignable(ApplicationContextInitializer.class, initializerClass);
            return initializerClass;
        } catch (ClassNotFoundException var3) {
            throw new ApplicationContextException("Failed to load context initializer class [" + className + "]", var3);
        }
    }

    private void applyInitializerClasses(ConfigurableApplicationContext context, List<Class<?>> initializerClasses) {
        Class<?> contextClass = context.getClass();
        List<ApplicationContextInitializer<?>> initializers = new ArrayList();
        Iterator var5 = initializerClasses.iterator();

        while(var5.hasNext()) {
            Class<?> initializerClass = (Class)var5.next();
            initializers.add(this.instantiateInitializer(contextClass, initializerClass));
        }

        this.applyInitializers(context, initializers);
    }

    private ApplicationContextInitializer<?> instantiateInitializer(Class<?> contextClass, Class<?> initializerClass) {
        Class<?> requireContextClass = GenericTypeResolver.resolveTypeArgument(initializerClass, ApplicationContextInitializer.class);
        Assert.isAssignable(requireContextClass, contextClass, String.format("Could not add context initializer [%s] as its generic parameter [%s] is not assignable from the type of application context used by this context loader [%s]: ", initializerClass.getName(), requireContextClass.getName(), contextClass.getName()));
        return (ApplicationContextInitializer)BeanUtils.instantiateClass(initializerClass);
    }

    private void applyInitializers(ConfigurableApplicationContext context, List<ApplicationContextInitializer<?>> initializers) {
        initializers.sort(new AnnotationAwareOrderComparator());
        Iterator var3 = initializers.iterator();

        while(var3.hasNext()) {
            ApplicationContextInitializer initializer = (ApplicationContextInitializer)var3.next();
            initializer.initialize(context);
        }

    }

    public void setOrder(int order) {
        this.order = order;
    }

    public int getOrder() {
        return this.order;
    }
}
