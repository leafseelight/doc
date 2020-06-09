package org.springframework.context.support;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.support.ResourceEditorRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.context.weaving.LoadTimeWeaverAware;
import org.springframework.context.weaving.LoadTimeWeaverAwareProcessor;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

public abstract class AbstractApplicationContext extends DefaultResourceLoader implements ConfigurableApplicationContext {
    public static final String MESSAGE_SOURCE_BEAN_NAME = "messageSource";
    public static final String LIFECYCLE_PROCESSOR_BEAN_NAME = "lifecycleProcessor";
    public static final String APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster";
    protected final Log logger;
    private String id;														// 应用上下文id
    private String displayName;												// 应用上下文名称
    @Nullable
    private ApplicationContext parent;										// 父应用上下文
    @Nullable
    private ConfigurableEnvironment environment;							// 环境
    private final List<BeanFactoryPostProcessor> beanFactoryPostProcessors; // bean工厂后置处理器
    private long startupDate;												// 启动日期
    private final AtomicBoolean active;										// 是否启动
    private final AtomicBoolean closed;										// 是否关闭
    private final Object startupShutdownMonitor;							// 用于启动停止时候同步的锁
    @Nullable
    private Thread shutdownHook;
    private ResourcePatternResolver resourcePatternResolver;				// 资源解析器
    @Nullable
    private LifecycleProcessor lifecycleProcessor;							// 申明周期处理器
    @Nullable
    private MessageSource messageSource;									// 消息来源
    @Nullable
    private ApplicationEventMulticaster applicationEventMulticaster;		// 应用事件广播器
    private final Set<ApplicationListener<?>> applicationListeners;			// 应用监听器
    @Nullable
    private Set<ApplicationListener<?>> earlyApplicationListeners;
    @Nullable
    private Set<ApplicationEvent> earlyApplicationEvents;

    public AbstractApplicationContext() {
        this.logger = LogFactory.getLog(this.getClass());
        this.id = ObjectUtils.identityToString(this);
        this.displayName = ObjectUtils.identityToString(this);
        this.beanFactoryPostProcessors = new ArrayList();
        this.active = new AtomicBoolean();
        this.closed = new AtomicBoolean();
        this.startupShutdownMonitor = new Object();
        this.applicationListeners = new LinkedHashSet();
        this.resourcePatternResolver = this.getResourcePatternResolver();
    }

    public AbstractApplicationContext(@Nullable ApplicationContext parent) {
        this();
        this.setParent(parent);
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }

    public String getApplicationName() {
        return "";
    }

    public void setDisplayName(String displayName) {
        Assert.hasLength(displayName, "Display name must not be empty");
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return this.displayName;
    }

    @Nullable
    public ApplicationContext getParent() {
        return this.parent;
    }

    public void setEnvironment(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    public ConfigurableEnvironment getEnvironment() {
        if (this.environment == null) {
            this.environment = this.createEnvironment();
        }

        return this.environment;
    }

    protected ConfigurableEnvironment createEnvironment() {
        return new StandardEnvironment();
    }

    public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
        return this.getBeanFactory();
    }

    public long getStartupDate() {
        return this.startupDate;
    }

    public void publishEvent(ApplicationEvent event) {
        this.publishEvent(event, (ResolvableType)null);
    }

    public void publishEvent(Object event) {
        this.publishEvent(event, (ResolvableType)null);
    }

    protected void publishEvent(Object event, @Nullable ResolvableType eventType) {
        Assert.notNull(event, "Event must not be null");
        Object applicationEvent;
        if (event instanceof ApplicationEvent) {
            applicationEvent = (ApplicationEvent)event;
        } else {
            applicationEvent = new PayloadApplicationEvent(this, event);
            if (eventType == null) {
                eventType = ((PayloadApplicationEvent)applicationEvent).getResolvableType();
            }
        }

        if (this.earlyApplicationEvents != null) {
            this.earlyApplicationEvents.add(applicationEvent);
        } else {
            this.getApplicationEventMulticaster().multicastEvent((ApplicationEvent)applicationEvent, eventType);
        }

        if (this.parent != null) {
            if (this.parent instanceof AbstractApplicationContext) {
                ((AbstractApplicationContext)this.parent).publishEvent(event, eventType);
            } else {
                this.parent.publishEvent(event);
            }
        }

    }

    ApplicationEventMulticaster getApplicationEventMulticaster() throws IllegalStateException {
        if (this.applicationEventMulticaster == null) {
            throw new IllegalStateException("ApplicationEventMulticaster not initialized - call 'refresh' before multicasting events via the context: " + this);
        } else {
            return this.applicationEventMulticaster;
        }
    }

    LifecycleProcessor getLifecycleProcessor() throws IllegalStateException {
        if (this.lifecycleProcessor == null) {
            throw new IllegalStateException("LifecycleProcessor not initialized - call 'refresh' before invoking lifecycle methods via the context: " + this);
        } else {
            return this.lifecycleProcessor;
        }
    }

    protected ResourcePatternResolver getResourcePatternResolver() {
        return new PathMatchingResourcePatternResolver(this);
    }

    public void setParent(@Nullable ApplicationContext parent) {
        this.parent = parent;
        if (parent != null) {
            Environment parentEnvironment = parent.getEnvironment();
            if (parentEnvironment instanceof ConfigurableEnvironment) {
                this.getEnvironment().merge((ConfigurableEnvironment)parentEnvironment);
            }
        }

    }

    public void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor) {
        Assert.notNull(postProcessor, "BeanFactoryPostProcessor must not be null");
        this.beanFactoryPostProcessors.add(postProcessor);
    }

    public List<BeanFactoryPostProcessor> getBeanFactoryPostProcessors() {
        return this.beanFactoryPostProcessors;
    }

    public void addApplicationListener(ApplicationListener<?> listener) {
        Assert.notNull(listener, "ApplicationListener must not be null");
        if (this.applicationEventMulticaster != null) {
            this.applicationEventMulticaster.addApplicationListener(listener);
        }

        this.applicationListeners.add(listener);
    }

    public Collection<ApplicationListener<?>> getApplicationListeners() {
        return this.applicationListeners;
    }

	// 刷新应用上下文
    public void refresh() throws BeansException, IllegalStateException {
        synchronized(this.startupShutdownMonitor) {
            this.prepareRefresh();
            ConfigurableListableBeanFactory beanFactory = this.obtainFreshBeanFactory();
            this.prepareBeanFactory(beanFactory);

            try {
                this.postProcessBeanFactory(beanFactory);
                this.invokeBeanFactoryPostProcessors(beanFactory);
                this.registerBeanPostProcessors(beanFactory);
                this.initMessageSource();
                this.initApplicationEventMulticaster();
                this.onRefresh();
                this.registerListeners();
                this.finishBeanFactoryInitialization(beanFactory);
                this.finishRefresh();
            } catch (BeansException var9) {
                if (this.logger.isWarnEnabled()) {
                    this.logger.warn("Exception encountered during context initialization - cancelling refresh attempt: " + var9);
                }

                this.destroyBeans();
                this.cancelRefresh(var9);
                throw var9;
            } finally {
                this.resetCommonCaches();
            }

        }
    }

    protected void prepareRefresh() {
        this.startupDate = System.currentTimeMillis();
        this.closed.set(false);
        this.active.set(true);
        if (this.logger.isDebugEnabled()) {
            if (this.logger.isTraceEnabled()) {
                this.logger.trace("Refreshing " + this);
            } else {
                this.logger.debug("Refreshing " + this.getDisplayName());
            }
        }

        this.initPropertySources();
        this.getEnvironment().validateRequiredProperties();
        if (this.earlyApplicationListeners == null) {
            this.earlyApplicationListeners = new LinkedHashSet(this.applicationListeners);
        } else {
            this.applicationListeners.clear();
            this.applicationListeners.addAll(this.earlyApplicationListeners);
        }

        this.earlyApplicationEvents = new LinkedHashSet();
    }

    protected void initPropertySources() {
    }

    protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
        this.refreshBeanFactory();
        return this.getBeanFactory();
    }

    protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        beanFactory.setBeanClassLoader(this.getClassLoader());
        beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
        beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, this.getEnvironment()));
        beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
        beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
        beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
        beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
        beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
        beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
        beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
        beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
        beanFactory.registerResolvableDependency(ResourceLoader.class, this);
        beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
        beanFactory.registerResolvableDependency(ApplicationContext.class, this);
        beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));
        if (beanFactory.containsBean("loadTimeWeaver")) {
            beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
            beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
        }

        if (!beanFactory.containsLocalBean("environment")) {
            beanFactory.registerSingleton("environment", this.getEnvironment());
        }

        if (!beanFactory.containsLocalBean("systemProperties")) {
            beanFactory.registerSingleton("systemProperties", this.getEnvironment().getSystemProperties());
        }

        if (!beanFactory.containsLocalBean("systemEnvironment")) {
            beanFactory.registerSingleton("systemEnvironment", this.getEnvironment().getSystemEnvironment());
        }

    }

    protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
    }

    protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
        PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, this.getBeanFactoryPostProcessors());
        if (beanFactory.getTempClassLoader() == null && beanFactory.containsBean("loadTimeWeaver")) {
            beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
            beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
        }

    }

    protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) {
        PostProcessorRegistrationDelegate.registerBeanPostProcessors(beanFactory, this);
    }

    protected void initMessageSource() {
        ConfigurableListableBeanFactory beanFactory = this.getBeanFactory();
        if (beanFactory.containsLocalBean("messageSource")) {
            this.messageSource = (MessageSource)beanFactory.getBean("messageSource", MessageSource.class);
            if (this.parent != null && this.messageSource instanceof HierarchicalMessageSource) {
                HierarchicalMessageSource hms = (HierarchicalMessageSource)this.messageSource;
                if (hms.getParentMessageSource() == null) {
                    hms.setParentMessageSource(this.getInternalParentMessageSource());
                }
            }

            if (this.logger.isTraceEnabled()) {
                this.logger.trace("Using MessageSource [" + this.messageSource + "]");
            }
        } else {
            DelegatingMessageSource dms = new DelegatingMessageSource();
            dms.setParentMessageSource(this.getInternalParentMessageSource());
            this.messageSource = dms;
            beanFactory.registerSingleton("messageSource", this.messageSource);
            if (this.logger.isTraceEnabled()) {
                this.logger.trace("No 'messageSource' bean, using [" + this.messageSource + "]");
            }
        }

    }

    protected void initApplicationEventMulticaster() {
        ConfigurableListableBeanFactory beanFactory = this.getBeanFactory();
        if (beanFactory.containsLocalBean("applicationEventMulticaster")) {
            this.applicationEventMulticaster = (ApplicationEventMulticaster)beanFactory.getBean("applicationEventMulticaster", ApplicationEventMulticaster.class);
            if (this.logger.isTraceEnabled()) {
                this.logger.trace("Using ApplicationEventMulticaster [" + this.applicationEventMulticaster + "]");
            }
        } else {
            this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
            beanFactory.registerSingleton("applicationEventMulticaster", this.applicationEventMulticaster);
            if (this.logger.isTraceEnabled()) {
                this.logger.trace("No 'applicationEventMulticaster' bean, using [" + this.applicationEventMulticaster.getClass().getSimpleName() + "]");
            }
        }

    }

    protected void initLifecycleProcessor() {
        ConfigurableListableBeanFactory beanFactory = this.getBeanFactory();
        if (beanFactory.containsLocalBean("lifecycleProcessor")) {
            this.lifecycleProcessor = (LifecycleProcessor)beanFactory.getBean("lifecycleProcessor", LifecycleProcessor.class);
            if (this.logger.isTraceEnabled()) {
                this.logger.trace("Using LifecycleProcessor [" + this.lifecycleProcessor + "]");
            }
        } else {
            DefaultLifecycleProcessor defaultProcessor = new DefaultLifecycleProcessor();
            defaultProcessor.setBeanFactory(beanFactory);
            this.lifecycleProcessor = defaultProcessor;
            beanFactory.registerSingleton("lifecycleProcessor", this.lifecycleProcessor);
            if (this.logger.isTraceEnabled()) {
                this.logger.trace("No 'lifecycleProcessor' bean, using [" + this.lifecycleProcessor.getClass().getSimpleName() + "]");
            }
        }

    }

    protected void onRefresh() throws BeansException {
    }

    protected void registerListeners() {
        Iterator var1 = this.getApplicationListeners().iterator();

        while(var1.hasNext()) {
            ApplicationListener<?> listener = (ApplicationListener)var1.next();
            this.getApplicationEventMulticaster().addApplicationListener(listener);
        }

        String[] listenerBeanNames = this.getBeanNamesForType(ApplicationListener.class, true, false);
        String[] var7 = listenerBeanNames;
        int var3 = listenerBeanNames.length;

        for(int var4 = 0; var4 < var3; ++var4) {
            String listenerBeanName = var7[var4];
            this.getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
        }

        Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
        this.earlyApplicationEvents = null;
        if (earlyEventsToProcess != null) {
            Iterator var9 = earlyEventsToProcess.iterator();

            while(var9.hasNext()) {
                ApplicationEvent earlyEvent = (ApplicationEvent)var9.next();
                this.getApplicationEventMulticaster().multicastEvent(earlyEvent);
            }
        }

    }

    protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
        if (beanFactory.containsBean("conversionService") && beanFactory.isTypeMatch("conversionService", ConversionService.class)) {
            beanFactory.setConversionService((ConversionService)beanFactory.getBean("conversionService", ConversionService.class));
        }

        if (!beanFactory.hasEmbeddedValueResolver()) {
            beanFactory.addEmbeddedValueResolver((strVal) -> {
                return this.getEnvironment().resolvePlaceholders(strVal);
            });
        }

        String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);
        String[] var3 = weaverAwareNames;
        int var4 = weaverAwareNames.length;

        for(int var5 = 0; var5 < var4; ++var5) {
            String weaverAwareName = var3[var5];
            this.getBean(weaverAwareName);
        }

        beanFactory.setTempClassLoader((ClassLoader)null);
        beanFactory.freezeConfiguration();
        beanFactory.preInstantiateSingletons();
    }

    protected void finishRefresh() {
        this.clearResourceCaches();
        this.initLifecycleProcessor();
        this.getLifecycleProcessor().onRefresh();
        this.publishEvent((ApplicationEvent)(new ContextRefreshedEvent(this)));
        LiveBeansView.registerApplicationContext(this);
    }

    protected void cancelRefresh(BeansException ex) {
        this.active.set(false);
    }

    protected void resetCommonCaches() {
        ReflectionUtils.clearCache();
        AnnotationUtils.clearCache();
        ResolvableType.clearCache();
        CachedIntrospectionResults.clearClassLoader(this.getClassLoader());
    }

    public void registerShutdownHook() {
        if (this.shutdownHook == null) {
            this.shutdownHook = new Thread() {
                public void run() {
                    synchronized(AbstractApplicationContext.this.startupShutdownMonitor) {
                        AbstractApplicationContext.this.doClose();
                    }
                }
            };
            Runtime.getRuntime().addShutdownHook(this.shutdownHook);
        }

    }

    /** @deprecated */
    @Deprecated
    public void destroy() {
        this.close();
    }

    public void close() {
        synchronized(this.startupShutdownMonitor) {
            this.doClose();
            if (this.shutdownHook != null) {
                try {
                    Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
                } catch (IllegalStateException var4) {
                }
            }

        }
    }

	// 关闭应用上下文
    protected void doClose() {
		// 设置关闭标识
        if (this.active.get() && this.closed.compareAndSet(false, true)) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Closing " + this);
            }

            LiveBeansView.unregisterApplicationContext(this);

            try {
				// 发布上下文关闭事件
                this.publishEvent((ApplicationEvent)(new ContextClosedEvent(this)));
            } catch (Throwable var3) {
                this.logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", var3);
            }

            if (this.lifecycleProcessor != null) {
                try {
					// 调用生命周期处理器的onClose
                    this.lifecycleProcessor.onClose();
                } catch (Throwable var2) {
                    this.logger.warn("Exception thrown from LifecycleProcessor on context close", var2);
                }
            }

			// 销毁所有bean
            this.destroyBeans();
			// 关闭bean工厂
            this.closeBeanFactory();
            this.onClose();
            if (this.earlyApplicationListeners != null) {
                this.applicationListeners.clear();
                this.applicationListeners.addAll(this.earlyApplicationListeners);
            }

            this.active.set(false);
        }

    }

    protected void destroyBeans() {
        this.getBeanFactory().destroySingletons();
    }

    protected void onClose() {
    }

    public boolean isActive() {
        return this.active.get();
    }

    protected void assertBeanFactoryActive() {
        if (!this.active.get()) {
            if (this.closed.get()) {
                throw new IllegalStateException(this.getDisplayName() + " has been closed already");
            } else {
                throw new IllegalStateException(this.getDisplayName() + " has not been refreshed yet");
            }
        }
    }

    public Object getBean(String name) throws BeansException {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getBean(name);
    }

    public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getBean(name, requiredType);
    }

    public Object getBean(String name, Object... args) throws BeansException {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getBean(name, args);
    }

    public <T> T getBean(Class<T> requiredType) throws BeansException {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getBean(requiredType);
    }

    public <T> T getBean(Class<T> requiredType, Object... args) throws BeansException {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getBean(requiredType, args);
    }

    public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getBeanProvider(requiredType);
    }

    public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getBeanProvider(requiredType);
    }

    public boolean containsBean(String name) {
        return this.getBeanFactory().containsBean(name);
    }

    public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().isSingleton(name);
    }

    public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().isPrototype(name);
    }

    public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().isTypeMatch(name, typeToMatch);
    }

    public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().isTypeMatch(name, typeToMatch);
    }

    @Nullable
    public Class<?> getType(String name) throws NoSuchBeanDefinitionException {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getType(name);
    }

    public String[] getAliases(String name) {
        return this.getBeanFactory().getAliases(name);
    }

    public boolean containsBeanDefinition(String beanName) {
        return this.getBeanFactory().containsBeanDefinition(beanName);
    }

    public int getBeanDefinitionCount() {
        return this.getBeanFactory().getBeanDefinitionCount();
    }

    public String[] getBeanDefinitionNames() {
        return this.getBeanFactory().getBeanDefinitionNames();
    }

    public String[] getBeanNamesForType(ResolvableType type) {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getBeanNamesForType(type);
    }

    public String[] getBeanNamesForType(@Nullable Class<?> type) {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getBeanNamesForType(type);
    }

    public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
    }

    public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getBeansOfType(type);
    }

    public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getBeansOfType(type, includeNonSingletons, allowEagerInit);
    }

    public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getBeanNamesForAnnotation(annotationType);
    }

    public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) throws BeansException {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().getBeansWithAnnotation(annotationType);
    }

    @Nullable
    public <A extends Annotation> A findAnnotationOnBean(String beanName, Class<A> annotationType) throws NoSuchBeanDefinitionException {
        this.assertBeanFactoryActive();
        return this.getBeanFactory().findAnnotationOnBean(beanName, annotationType);
    }

    @Nullable
    public BeanFactory getParentBeanFactory() {
        return this.getParent();
    }

    public boolean containsLocalBean(String name) {
        return this.getBeanFactory().containsLocalBean(name);
    }

    @Nullable
    protected BeanFactory getInternalParentBeanFactory() {
        return (BeanFactory)(this.getParent() instanceof ConfigurableApplicationContext ? ((ConfigurableApplicationContext)this.getParent()).getBeanFactory() : this.getParent());
    }

    public String getMessage(String code, @Nullable Object[] args, @Nullable String defaultMessage, Locale locale) {
        return this.getMessageSource().getMessage(code, args, defaultMessage, locale);
    }

    public String getMessage(String code, @Nullable Object[] args, Locale locale) throws NoSuchMessageException {
        return this.getMessageSource().getMessage(code, args, locale);
    }

    public String getMessage(MessageSourceResolvable resolvable, Locale locale) throws NoSuchMessageException {
        return this.getMessageSource().getMessage(resolvable, locale);
    }

    private MessageSource getMessageSource() throws IllegalStateException {
        if (this.messageSource == null) {
            throw new IllegalStateException("MessageSource not initialized - call 'refresh' before accessing messages via the context: " + this);
        } else {
            return this.messageSource;
        }
    }

    @Nullable
    protected MessageSource getInternalParentMessageSource() {
        return (MessageSource)(this.getParent() instanceof AbstractApplicationContext ? ((AbstractApplicationContext)this.getParent()).messageSource : this.getParent());
    }

    public Resource[] getResources(String locationPattern) throws IOException {
        return this.resourcePatternResolver.getResources(locationPattern);
    }

    public void start() {
        this.getLifecycleProcessor().start();
        this.publishEvent((ApplicationEvent)(new ContextStartedEvent(this)));
    }

    public void stop() {
        this.getLifecycleProcessor().stop();
        this.publishEvent((ApplicationEvent)(new ContextStoppedEvent(this)));
    }

    public boolean isRunning() {
        return this.lifecycleProcessor != null && this.lifecycleProcessor.isRunning();
    }

    protected abstract void refreshBeanFactory() throws BeansException, IllegalStateException;

    protected abstract void closeBeanFactory();

    public abstract ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;

    public String toString() {
        StringBuilder sb = new StringBuilder(this.getDisplayName());
        sb.append(", started on ").append(new Date(this.getStartupDate()));
        ApplicationContext parent = this.getParent();
        if (parent != null) {
            sb.append(", parent: ").append(parent.getDisplayName());
        }

        return sb.toString();
    }

    static {
        ContextClosedEvent.class.getName();
    }
}
