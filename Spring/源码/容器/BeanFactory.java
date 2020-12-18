package org.springframework.beans.factory;

import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

public interface BeanFactory {
	// 工厂bean名称前缀为&
    String FACTORY_BEAN_PREFIX = "&";

	// 根据bean name获取bean实例
    Object getBean(String var1) throws BeansException;

    <T> T getBean(String var1, Class<T> var2) throws BeansException;

    Object getBean(String var1, Object... var2) throws BeansException;

	// 根据类型获取bean实例
    <T> T getBean(Class<T> var1) throws BeansException;

    <T> T getBean(Class<T> var1, Object... var2) throws BeansException;

    <T> ObjectProvider<T> getBeanProvider(Class<T> var1);

    <T> ObjectProvider<T> getBeanProvider(ResolvableType var1);

	// 容器是否存在某个bean
    boolean containsBean(String var1);

	// 判断bean是否单例
    boolean isSingleton(String var1) throws NoSuchBeanDefinitionException;

	// 判断bean是否多例
    boolean isPrototype(String var1) throws NoSuchBeanDefinitionException;

	// 判断bean是否是某个类型
    boolean isTypeMatch(String var1, ResolvableType var2) throws NoSuchBeanDefinitionException;

	// 判断bean是否是某个Class类型
    boolean isTypeMatch(String var1, Class<?> var2) throws NoSuchBeanDefinitionException;

	// 获取bean的Class类型
    @Nullable
    Class<?> getType(String var1) throws NoSuchBeanDefinitionException;

	// 获取所有别名
    String[] getAliases(String var1);
}
