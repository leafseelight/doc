package org.springframework.beans.factory.support;

import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public abstract class AbstractBeanDefinitionReader implements BeanDefinitionReader, EnvironmentCapable {
    protected final Log logger = LogFactory.getLog(this.getClass());
    private final BeanDefinitionRegistry registry;
    @Nullable
    private ResourceLoader resourceLoader;
    @Nullable
    private ClassLoader beanClassLoader;
    private Environment environment;
    private BeanNameGenerator beanNameGenerator = new DefaultBeanNameGenerator();

    protected AbstractBeanDefinitionReader(BeanDefinitionRegistry registry) {
        Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
        this.registry = registry;
        if (this.registry instanceof ResourceLoader) {
            this.resourceLoader = (ResourceLoader)this.registry;
        } else {
            this.resourceLoader = new PathMatchingResourcePatternResolver();
        }

        if (this.registry instanceof EnvironmentCapable) {
            this.environment = ((EnvironmentCapable)this.registry).getEnvironment();
        } else {
            this.environment = new StandardEnvironment();
        }

    }

    public final BeanDefinitionRegistry getBeanFactory() {
        return this.registry;
    }

    public final BeanDefinitionRegistry getRegistry() {
        return this.registry;
    }

    public void setResourceLoader(@Nullable ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Nullable
    public ResourceLoader getResourceLoader() {
        return this.resourceLoader;
    }

    public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
        this.beanClassLoader = beanClassLoader;
    }

    @Nullable
    public ClassLoader getBeanClassLoader() {
        return this.beanClassLoader;
    }

    public void setEnvironment(Environment environment) {
        Assert.notNull(environment, "Environment must not be null");
        this.environment = environment;
    }

    public Environment getEnvironment() {
        return this.environment;
    }

    public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
        this.beanNameGenerator = (BeanNameGenerator)(beanNameGenerator != null ? beanNameGenerator : new DefaultBeanNameGenerator());
    }

    public BeanNameGenerator getBeanNameGenerator() {
        return this.beanNameGenerator;
    }

    public int loadBeanDefinitions(Resource... resources) throws BeanDefinitionStoreException {
        Assert.notNull(resources, "Resource array must not be null");
        int count = 0;
        Resource[] var3 = resources;
        int var4 = resources.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            Resource resource = var3[var5];
            count += this.loadBeanDefinitions((Resource)resource);
        }

        return count;
    }

    public int loadBeanDefinitions(String location) throws BeanDefinitionStoreException {
        return this.loadBeanDefinitions(location, (Set)null);
    }

    public int loadBeanDefinitions(String location, @Nullable Set<Resource> actualResources) throws BeanDefinitionStoreException {
        ResourceLoader resourceLoader = this.getResourceLoader();
        if (resourceLoader == null) {
            throw new BeanDefinitionStoreException("Cannot load bean definitions from location [" + location + "]: no ResourceLoader available");
        } else {
            int count;
            if (resourceLoader instanceof ResourcePatternResolver) {
                try {
                    Resource[] resources = ((ResourcePatternResolver)resourceLoader).getResources(location);
                    count = this.loadBeanDefinitions(resources);
                    if (actualResources != null) {
                        Collections.addAll(actualResources, resources);
                    }

                    if (this.logger.isTraceEnabled()) {
                        this.logger.trace("Loaded " + count + " bean definitions from location pattern [" + location + "]");
                    }

                    return count;
                } catch (IOException var6) {
                    throw new BeanDefinitionStoreException("Could not resolve bean definition resource pattern [" + location + "]", var6);
                }
            } else {
                Resource resource = resourceLoader.getResource(location);
                count = this.loadBeanDefinitions((Resource)resource);
                if (actualResources != null) {
                    actualResources.add(resource);
                }

                if (this.logger.isTraceEnabled()) {
                    this.logger.trace("Loaded " + count + " bean definitions from location [" + location + "]");
                }

                return count;
            }
        }
    }

    public int loadBeanDefinitions(String... locations) throws BeanDefinitionStoreException {
        Assert.notNull(locations, "Location array must not be null");
        int count = 0;
        String[] var3 = locations;
        int var4 = locations.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            String location = var3[var5];
            count += this.loadBeanDefinitions(location);
        }

        return count;
    }
}
