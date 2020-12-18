package org.springframework.beans.factory.support;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;

public interface BeanDefinitionReader {
    BeanDefinitionRegistry getRegistry();

    @Nullable
    ResourceLoader getResourceLoader();

    @Nullable
    ClassLoader getBeanClassLoader();

    BeanNameGenerator getBeanNameGenerator();

	// 加载bean definition到registry
    int loadBeanDefinitions(Resource var1) throws BeanDefinitionStoreException;

    int loadBeanDefinitions(Resource... var1) throws BeanDefinitionStoreException;

    int loadBeanDefinitions(String var1) throws BeanDefinitionStoreException;

    int loadBeanDefinitions(String... var1) throws BeanDefinitionStoreException;
}
