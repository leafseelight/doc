package org.springframework.beans.factory;

import java.lang.annotation.Annotation;
import java.util.Map;
import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;
import org.springframework.lang.Nullable;

public interface ListableBeanFactory extends BeanFactory {
	
	// 是否包含bean definition
    boolean containsBeanDefinition(String var1);

	// 获取bean definition数
    int getBeanDefinitionCount();

	// 获取所有的bean definition的名字
    String[] getBeanDefinitionNames();

	// 根据类型获取bean名称
    String[] getBeanNamesForType(ResolvableType var1);

    String[] getBeanNamesForType(@Nullable Class<?> var1);

    String[] getBeanNamesForType(@Nullable Class<?> var1, boolean var2, boolean var3);

	// 根据类型获取所有的bean实例
    <T> Map<String, T> getBeansOfType(@Nullable Class<T> var1) throws BeansException;

    <T> Map<String, T> getBeansOfType(@Nullable Class<T> var1, boolean var2, boolean var3) throws BeansException;

	// 根据注解获取所有的bean名称
    String[] getBeanNamesForAnnotation(Class<? extends Annotation> var1);

	// 根据注解获取所有的bean实例
    Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> var1) throws BeansException;

	// 获取bean上的注解
    @Nullable
    <A extends Annotation> A findAnnotationOnBean(String var1, Class<A> var2) throws NoSuchBeanDefinitionException;
}
