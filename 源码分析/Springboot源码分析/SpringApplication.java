//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.springframework.boot;

import java.lang.reflect.Constructor;
import java.security.AccessControlException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.boot.Banner.Mode;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.web.reactive.context.StandardReactiveWebEnvironment;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StopWatch;
import org.springframework.util.StringUtils;
import org.springframework.web.context.support.StandardServletEnvironment;

public class SpringApplication {
    public static final String DEFAULT_CONTEXT_CLASS = "org.springframework.context.annotation.AnnotationConfigApplicationContext";                                      // ???????????????
    public static final String DEFAULT_SERVLET_WEB_CONTEXT_CLASS = "org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext";    // ??????web?????????
    public static final String DEFAULT_REACTIVE_WEB_CONTEXT_CLASS = "org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext"; // ??????reactive?????????
    public static final String BANNER_LOCATION_PROPERTY_VALUE = "banner.txt";            // banner?????????
    public static final String BANNER_LOCATION_PROPERTY = "spring.banner.location";      // banner???????????? 
    private static final String SYSTEM_PROPERTY_JAVA_AWT_HEADLESS = "java.awt.headless"; // ??????headless????????????
    private static final Log logger = LogFactory.getLog(SpringApplication.class);        // ??????
    private Set<Class<?>> primarySources;         // ???????????????class??????
    private Set<String> sources;
    private Class<?> mainApplicationClass;        // main????????????
    private Mode bannerMode;                      // banner??????
    private boolean logStartupInfo;
    private boolean addCommandLineProperties;     // ???????????????????????????
    private Banner banner;                        // banner 
    private ResourceLoader resourceLoader;        // ???????????????
    private BeanNameGenerator beanNameGenerator;  // bean name????????? ??????bean name???????????????
    private ConfigurableEnvironment environment;  // ???????????? ????????????Profile???????????????
    private Class<? extends ConfigurableApplicationContext> applicationContextClass; // ???????????????class??????
    private WebApplicationType webApplicationType;                 // web???????????? (NONE,SERVLET,REACTIVE;)
    private boolean headless;                                      // ????????????headless??????
    private boolean registerShutdownHook;                          // ????????????shutdown??????
    private List<ApplicationContextInitializer<?>> initializers;   // ??????????????????????????? ??????????????????refresh??????????????????????????????
    private List<ApplicationListener<?>> listeners;                // ???????????????
    private Map<String, Object> defaultProperties;                 // ????????????
    private Set<String> additionalProfiles;                        // ?????????profiles
    private boolean isCustomEnvironment;                           // ???????????????environment

    public SpringApplication(Class... primarySources) {
        this((ResourceLoader)null, primarySources);
    }

    public SpringApplication(ResourceLoader resourceLoader, Class... primarySources) {
        this.sources = new LinkedHashSet();
        this.bannerMode = Mode.CONSOLE;           // ????????????????????????banner
        this.logStartupInfo = true;
        this.addCommandLineProperties = true;     // commandLine??????
        this.headless = true;
        this.registerShutdownHook = true;         // ??????????????????????????????
        this.additionalProfiles = new HashSet();
        this.isCustomEnvironment = false;
        this.resourceLoader = resourceLoader;
        Assert.notNull(primarySources, "PrimarySources must not be null");
        this.primarySources = new LinkedHashSet(Arrays.asList(primarySources));
        this.webApplicationType = WebApplicationType.deduceFromClasspath();                            // ??????classpath???????????????web????????????
        this.setInitializers(this.getSpringFactoriesInstances(ApplicationContextInitializer.class));   // ???META-INF/spring.factories??????ApplicationContextInitializer???????????????????????? ?????????initializers??????
        this.setListeners(this.getSpringFactoriesInstances(ApplicationListener.class));                // ???META-INF/spring.factories??????ApplicationListener???????????????????????? ?????????listeners??????
        this.mainApplicationClass = this.deduceMainApplicationClass();                                 // ??????????????????class??????
    }

	/*
	 * ???????????????????????????
	 *        ??????????????????RuntimeException ????????????????????? 
	 *        ????????????main?????????????????????declaringClass??????????????????
	 */
    private Class<?> deduceMainApplicationClass() {
        try {
            StackTraceElement[] stackTrace = (new RuntimeException()).getStackTrace();
            StackTraceElement[] var2 = stackTrace;
            int var3 = stackTrace.length;

            for(int var4 = 0; var4 < var3; ++var4) {
                StackTraceElement stackTraceElement = var2[var4];
                if ("main".equals(stackTraceElement.getMethodName())) {
                    return Class.forName(stackTraceElement.getClassName());
                }
            }
        } catch (ClassNotFoundException var6) {
            ;
        }

        return null;
    }

	/*
	 * ?????????????????????
	 */
    public ConfigurableApplicationContext run(String... args) {
        StopWatch stopWatch = new StopWatch();   // ????????????????????????
        stopWatch.start();                       // ????????????
        ConfigurableApplicationContext context = null;
        Collection<SpringBootExceptionReporter> exceptionReporters = new ArrayList();
        this.configureHeadlessProperty();        // ??????headless????????????
        SpringApplicationRunListeners listeners = this.getRunListeners(args);   // ????????????SpringApplicationRunListeners????????????
        listeners.starting();

        Collection exceptionReporters;
        try {
            ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);                // ????????????
            ConfigurableEnvironment environment = this.prepareEnvironment(listeners, applicationArguments);   // ??????environment ???environment??????propertySource???profile
            this.configureIgnoreBeanInfo(environment);                                                        // ??????spring.beaninfo.ignore?????? ?????????true ???????????????BeanInfo???
            Banner printedBanner = this.printBanner(environment);                                             // ??????banner
            context = this.createApplicationContext();                                                        // ?????????????????????
            exceptionReporters = this.getSpringFactoriesInstances(SpringBootExceptionReporter.class, new Class[]{ConfigurableApplicationContext.class}, context);
            this.prepareContext(context, environment, listeners, applicationArguments, printedBanner);        // ??????????????????????????????
            this.refreshContext(context);                                                                     // TODO ?????? Ioc????????????refresh??????
            this.afterRefresh(context, applicationArguments);                                                 // refresh??????????????? ?????????
            stopWatch.stop();                   // ????????????
            if (this.logStartupInfo) {
                (new StartupInfoLogger(this.mainApplicationClass)).logStarted(this.getApplicationLog(), stopWatch);
            }

            listeners.started(context);                             // ?????????????????????SpringApplicationRunListener???environmentPrepared??????
            this.callRunners(context, applicationArguments);        // ??????CommandLineRunner
        } catch (Throwable var10) {
            this.handleRunFailure(context, var10, exceptionReporters, listeners);
            throw new IllegalStateException(var10);
        }

        try {
            listeners.running(context);
            return context;
        } catch (Throwable var9) {
            this.handleRunFailure(context, var9, exceptionReporters, (SpringApplicationRunListeners)null);
            throw new IllegalStateException(var9);
        }
    }

    private ConfigurableEnvironment prepareEnvironment(SpringApplicationRunListeners listeners, ApplicationArguments applicationArguments) {
        ConfigurableEnvironment environment = this.getOrCreateEnvironment();                                          // ??????web???????????????????????????environment
        this.configureEnvironment((ConfigurableEnvironment)environment, applicationArguments.getSourceArgs());        // ?????? ?????????PropertySource???Profile
        listeners.environmentPrepared((ConfigurableEnvironment)environment);                                          // ?????????????????????SpringApplicationRunListener???environmentPrepared??????
        this.bindToSpringApplication((ConfigurableEnvironment)environment);                                           // TODO ???
        if (!this.isCustomEnvironment) {
            environment = (new EnvironmentConverter(this.getClassLoader())).convertEnvironmentIfNecessary((ConfigurableEnvironment)environment, this.deduceEnvironmentClass());
        }

        ConfigurationPropertySources.attach((Environment)environment);
        return (ConfigurableEnvironment)environment;
    }

	/*
	 * ??????web??????????????????environment???????????????
	 */
    private Class<? extends StandardEnvironment> deduceEnvironmentClass() {
        switch(this.webApplicationType) {
        case SERVLET:
            return StandardServletEnvironment.class;
        case REACTIVE:
            return StandardReactiveWebEnvironment.class;
        default:
            return StandardEnvironment.class;
        }
    }

    private void prepareContext(ConfigurableApplicationContext context, ConfigurableEnvironment environment, SpringApplicationRunListeners listeners, ApplicationArguments applicationArguments, Banner printedBanner) {
        context.setEnvironment(environment);
        this.postProcessApplicationContext(context);   // ??????bean generator???resource loader
        this.applyInitializers(context);               // ??????ApplicationContextInitializer????????? ?????????ApplicationContextInitializer???initialize??????
        listeners.contextPrepared(context);            // ?????????????????????SpringApplicationRunListener???contextPrepared??????
        if (this.logStartupInfo) {
            this.logStartupInfo(context.getParent() == null);   // ??????????????????
            this.logStartupProfileInfo(context);                // ??????profile??????
        }

        context.getBeanFactory().registerSingleton("springApplicationArguments", applicationArguments);  // ??????springApplicationArguments??????
        if (printedBanner != null) {
            context.getBeanFactory().registerSingleton("springBootBanner", printedBanner);               // ??????springBootBanner??????
        }

        Set<Object> sources = this.getAllSources();
        Assert.notEmpty(sources, "Sources must not be empty");
        this.load(context, sources.toArray(new Object[0]));   // ?????????????????????bean
        listeners.contextLoaded(context);                     // ?????????????????????SpringApplicationRunListener???contextLoaded??????
    }

    private void refreshContext(ConfigurableApplicationContext context) {
        this.refresh(context);
        if (this.registerShutdownHook) {
            try {
                context.registerShutdownHook();
            } catch (AccessControlException var3) {
                ;
            }
        }

    }

    private void configureHeadlessProperty() {
        System.setProperty("java.awt.headless", System.getProperty("java.awt.headless", Boolean.toString(this.headless)));
    }

    private SpringApplicationRunListeners getRunListeners(String[] args) {
        Class<?>[] types = new Class[]{SpringApplication.class, String[].class};
        return new SpringApplicationRunListeners(logger, this.getSpringFactoriesInstances(SpringApplicationRunListener.class, types, this, args));
    }

    private <T> Collection<T> getSpringFactoriesInstances(Class<T> type) {
        return this.getSpringFactoriesInstances(type, new Class[0]);
    }

    private <T> Collection<T> getSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes, Object... args) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();                                 // ????????????????????????????????? ????????????META-INF/spring.factories????????????
        Set<String> names = new LinkedHashSet(SpringFactoriesLoader.loadFactoryNames(type, classLoader));         // SpringFactoriesLoader????????????META-INF/spring.factories???????????????????????????
        List<T> instances = this.createSpringFactoriesInstances(type, parameterTypes, classLoader, args, names);  // ???????????????????????????
        AnnotationAwareOrderComparator.sort(instances);  // ??????@Order??????
        return instances;
    }

    private <T> List<T> createSpringFactoriesInstances(Class<T> type, Class<?>[] parameterTypes, ClassLoader classLoader, Object[] args, Set<String> names) {
        List<T> instances = new ArrayList(names.size());
        Iterator var7 = names.iterator();

        while(var7.hasNext()) {
            String name = (String)var7.next();

            try {
                Class<?> instanceClass = ClassUtils.forName(name, classLoader);  
                Assert.isAssignable(type, instanceClass);
                Constructor<?> constructor = instanceClass.getDeclaredConstructor(parameterTypes);
                T instance = BeanUtils.instantiateClass(constructor, args);   // ?????????????????????
                instances.add(instance);
            } catch (Throwable var12) {
                throw new IllegalArgumentException("Cannot instantiate " + type + " : " + name, var12);
            }
        }

        return instances;
    }

    private ConfigurableEnvironment getOrCreateEnvironment() {
        if (this.environment != null) {
            return this.environment;
        } else {
            switch(this.webApplicationType) {
            case SERVLET:
                return new StandardServletEnvironment();
            case REACTIVE:
                return new StandardReactiveWebEnvironment();
            default:
                return new StandardEnvironment();
            }
        }
    }

	/*
	 * ??????environment 
	 *     
	 */
    protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
        this.configurePropertySources(environment, args);
        this.configureProfiles(environment, args);
    }

	/*
	 * ??????PropertySources 
	 *     ??????this??????????????????????????? ?????????
	 */
    protected void configurePropertySources(ConfigurableEnvironment environment, String[] args) {
        MutablePropertySources sources = environment.getPropertySources();
        if (this.defaultProperties != null && !this.defaultProperties.isEmpty()) {
            sources.addLast(new MapPropertySource("defaultProperties", this.defaultProperties));    // ??????????????????(map??????)?????????source???
        }

        if (this.addCommandLineProperties && args.length > 0) {
            String name = "commandLineArgs";                        // ?????????????????????
            if (sources.contains(name)) {
                PropertySource<?> source = sources.get(name);
                CompositePropertySource composite = new CompositePropertySource(name);
                composite.addPropertySource(new SimpleCommandLinePropertySource("springApplicationCommandLineArgs", args));
                composite.addPropertySource(source);
                sources.replace(name, composite);
            } else {
                sources.addFirst(new SimpleCommandLinePropertySource(args));   
            }
        }

    }

	/*
	 * ??????profiles 
	 *       ??????this?????????????????????profiles
	 */
    protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
        environment.getActiveProfiles();
        Set<String> profiles = new LinkedHashSet(this.additionalProfiles);
        profiles.addAll(Arrays.asList(environment.getActiveProfiles()));
        environment.setActiveProfiles(StringUtils.toStringArray(profiles));
    }

    private void configureIgnoreBeanInfo(ConfigurableEnvironment environment) {
        if (System.getProperty("spring.beaninfo.ignore") == null) {
            Boolean ignore = (Boolean)environment.getProperty("spring.beaninfo.ignore", Boolean.class, Boolean.TRUE);
            System.setProperty("spring.beaninfo.ignore", ignore.toString());
        }

    }

    protected void bindToSpringApplication(ConfigurableEnvironment environment) {
        try {
            Binder.get(environment).bind("spring.main", Bindable.ofInstance(this));
        } catch (Exception var3) {
            throw new IllegalStateException("Cannot bind to SpringApplication", var3);
        }
    }

    private Banner printBanner(ConfigurableEnvironment environment) {
        if (this.bannerMode == Mode.OFF) {
            return null;
        } else {
            ResourceLoader resourceLoader = this.resourceLoader != null ? this.resourceLoader : new DefaultResourceLoader(this.getClassLoader());
            SpringApplicationBannerPrinter bannerPrinter = new SpringApplicationBannerPrinter((ResourceLoader)resourceLoader, this.banner);
            return this.bannerMode == Mode.LOG ? bannerPrinter.print(environment, this.mainApplicationClass, logger) : bannerPrinter.print(environment, this.mainApplicationClass, System.out);
        }
    }

    protected ConfigurableApplicationContext createApplicationContext() {
        Class<?> contextClass = this.applicationContextClass;
        if (contextClass == null) {
            try {
                switch(this.webApplicationType) {
                case SERVLET:
                    contextClass = Class.forName("org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext");
                    break;
                case REACTIVE:
                    contextClass = Class.forName("org.springframework.boot.web.reactive.context.AnnotationConfigReactiveWebServerApplicationContext");
                    break;
                default:
                    contextClass = Class.forName("org.springframework.context.annotation.AnnotationConfigApplicationContext");
                }
            } catch (ClassNotFoundException var3) {
                throw new IllegalStateException("Unable create a default ApplicationContext, please specify an ApplicationContextClass", var3);
            }
        }

        return (ConfigurableApplicationContext)BeanUtils.instantiateClass(contextClass);
    }

    protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
        if (this.beanNameGenerator != null) {
            context.getBeanFactory().registerSingleton("org.springframework.context.annotation.internalConfigurationBeanNameGenerator", this.beanNameGenerator);
        }

        if (this.resourceLoader != null) {
            if (context instanceof GenericApplicationContext) {
                ((GenericApplicationContext)context).setResourceLoader(this.resourceLoader);
            }

            if (context instanceof DefaultResourceLoader) {
                ((DefaultResourceLoader)context).setClassLoader(this.resourceLoader.getClassLoader());
            }
        }

    }

    protected void applyInitializers(ConfigurableApplicationContext context) {
        Iterator var2 = this.getInitializers().iterator();

        while(var2.hasNext()) {
            ApplicationContextInitializer initializer = (ApplicationContextInitializer)var2.next();
            Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(initializer.getClass(), ApplicationContextInitializer.class);
            Assert.isInstanceOf(requiredType, context, "Unable to call initializer.");
            initializer.initialize(context);
        }

    }

    protected void logStartupInfo(boolean isRoot) {
        if (isRoot) {
            (new StartupInfoLogger(this.mainApplicationClass)).logStarting(this.getApplicationLog());
        }

    }

    protected void logStartupProfileInfo(ConfigurableApplicationContext context) {
        Log log = this.getApplicationLog();
        if (log.isInfoEnabled()) {
            String[] activeProfiles = context.getEnvironment().getActiveProfiles();
            if (ObjectUtils.isEmpty(activeProfiles)) {
                String[] defaultProfiles = context.getEnvironment().getDefaultProfiles();
                log.info("No active profile set, falling back to default profiles: " + StringUtils.arrayToCommaDelimitedString(defaultProfiles));
            } else {
                log.info("The following profiles are active: " + StringUtils.arrayToCommaDelimitedString(activeProfiles));
            }
        }

    }

    protected Log getApplicationLog() {
        return this.mainApplicationClass == null ? logger : LogFactory.getLog(this.mainApplicationClass);
    }

	/* 
	 * ???????????? ??????bean
	 */
    protected void load(ApplicationContext context, Object[] sources) {
        if (logger.isDebugEnabled()) {
            logger.debug("Loading source " + StringUtils.arrayToCommaDelimitedString(sources));
        }

        BeanDefinitionLoader loader = this.createBeanDefinitionLoader(this.getBeanDefinitionRegistry(context), sources);
        if (this.beanNameGenerator != null) {
            loader.setBeanNameGenerator(this.beanNameGenerator);
        }

        if (this.resourceLoader != null) {
            loader.setResourceLoader(this.resourceLoader);
        }

        if (this.environment != null) {
            loader.setEnvironment(this.environment);
        }

        loader.load();  // ??????bean???????????????
    }

    public ResourceLoader getResourceLoader() {
        return this.resourceLoader;
    }

    public ClassLoader getClassLoader() {
        return this.resourceLoader != null ? this.resourceLoader.getClassLoader() : ClassUtils.getDefaultClassLoader();
    }

	/*
	 * ??????Bean???????????????
	 */
    private BeanDefinitionRegistry getBeanDefinitionRegistry(ApplicationContext context) {
        if (context instanceof BeanDefinitionRegistry) {
            return (BeanDefinitionRegistry)context;
        } else if (context instanceof AbstractApplicationContext) {
            return (BeanDefinitionRegistry)((AbstractApplicationContext)context).getBeanFactory();
        } else {
            throw new IllegalStateException("Could not locate BeanDefinitionRegistry");
        }
    }

	/*
	 * ??????bean???????????????
	 */
    protected BeanDefinitionLoader createBeanDefinitionLoader(BeanDefinitionRegistry registry, Object[] sources) {
        return new BeanDefinitionLoader(registry, sources);
    }

    protected void refresh(ApplicationContext applicationContext) {
        Assert.isInstanceOf(AbstractApplicationContext.class, applicationContext);
        ((AbstractApplicationContext)applicationContext).refresh();
    }

    protected void afterRefresh(ConfigurableApplicationContext context, ApplicationArguments args) {
    }

    private void callRunners(ApplicationContext context, ApplicationArguments args) {
        List<Object> runners = new ArrayList();
        runners.addAll(context.getBeansOfType(ApplicationRunner.class).values());
        runners.addAll(context.getBeansOfType(CommandLineRunner.class).values());
        AnnotationAwareOrderComparator.sort(runners);
        Iterator var4 = (new LinkedHashSet(runners)).iterator();

        while(var4.hasNext()) {
            Object runner = var4.next();
            if (runner instanceof ApplicationRunner) {
                this.callRunner((ApplicationRunner)runner, args);
            }

            if (runner instanceof CommandLineRunner) {
                this.callRunner((CommandLineRunner)runner, args);
            }
        }

    }

    private void callRunner(ApplicationRunner runner, ApplicationArguments args) {
        try {
            runner.run(args);
        } catch (Exception var4) {
            throw new IllegalStateException("Failed to execute ApplicationRunner", var4);
        }
    }

    private void callRunner(CommandLineRunner runner, ApplicationArguments args) {
        try {
            runner.run(args.getSourceArgs());
        } catch (Exception var4) {
            throw new IllegalStateException("Failed to execute CommandLineRunner", var4);
        }
    }

    private void handleRunFailure(ConfigurableApplicationContext context, Throwable exception, Collection<SpringBootExceptionReporter> exceptionReporters, SpringApplicationRunListeners listeners) {
        try {
            try {
                this.handleExitCode(context, exception);
                if (listeners != null) {
                    listeners.failed(context, exception);
                }
            } finally {
                this.reportFailure(exceptionReporters, exception);
                if (context != null) {
                    context.close();
                }

            }
        } catch (Exception var9) {
            logger.warn("Unable to close ApplicationContext", var9);
        }

        ReflectionUtils.rethrowRuntimeException(exception);
    }

    private void reportFailure(Collection<SpringBootExceptionReporter> exceptionReporters, Throwable failure) {
        try {
            Iterator var3 = exceptionReporters.iterator();

            while(var3.hasNext()) {
                SpringBootExceptionReporter reporter = (SpringBootExceptionReporter)var3.next();
                if (reporter.reportException(failure)) {
                    this.registerLoggedException(failure);
                    return;
                }
            }
        } catch (Throwable var5) {
            ;
        }

        if (logger.isErrorEnabled()) {
            logger.error("Application run failed", failure);
            this.registerLoggedException(failure);
        }

    }

    protected void registerLoggedException(Throwable exception) {
        SpringBootExceptionHandler handler = this.getSpringBootExceptionHandler();
        if (handler != null) {
            handler.registerLoggedException(exception);
        }

    }

    private void handleExitCode(ConfigurableApplicationContext context, Throwable exception) {
        int exitCode = this.getExitCodeFromException(context, exception);
        if (exitCode != 0) {
            if (context != null) {
                context.publishEvent(new ExitCodeEvent(context, exitCode));
            }

            SpringBootExceptionHandler handler = this.getSpringBootExceptionHandler();
            if (handler != null) {
                handler.registerExitCode(exitCode);
            }
        }

    }

    private int getExitCodeFromException(ConfigurableApplicationContext context, Throwable exception) {
        int exitCode = this.getExitCodeFromMappedException(context, exception);
        if (exitCode == 0) {
            exitCode = this.getExitCodeFromExitCodeGeneratorException(exception);
        }

        return exitCode;
    }

    private int getExitCodeFromMappedException(ConfigurableApplicationContext context, Throwable exception) {
        if (context != null && context.isActive()) {
            ExitCodeGenerators generators = new ExitCodeGenerators();
            Collection<ExitCodeExceptionMapper> beans = context.getBeansOfType(ExitCodeExceptionMapper.class).values();
            generators.addAll(exception, beans);
            return generators.getExitCode();
        } else {
            return 0;
        }
    }

    private int getExitCodeFromExitCodeGeneratorException(Throwable exception) {
        if (exception == null) {
            return 0;
        } else {
            return exception instanceof ExitCodeGenerator ? ((ExitCodeGenerator)exception).getExitCode() : this.getExitCodeFromExitCodeGeneratorException(exception.getCause());
        }
    }

    SpringBootExceptionHandler getSpringBootExceptionHandler() {
        return this.isMainThread(Thread.currentThread()) ? SpringBootExceptionHandler.forCurrentThread() : null;
    }

    private boolean isMainThread(Thread currentThread) {
        return ("main".equals(currentThread.getName()) || "restartedMain".equals(currentThread.getName())) && "main".equals(currentThread.getThreadGroup().getName());
    }

    public Class<?> getMainApplicationClass() {
        return this.mainApplicationClass;
    }

    public void setMainApplicationClass(Class<?> mainApplicationClass) {
        this.mainApplicationClass = mainApplicationClass;
    }

    /** @deprecated */
    @Deprecated
    public boolean isWebEnvironment() {
        return this.webApplicationType == WebApplicationType.SERVLET;
    }

    public WebApplicationType getWebApplicationType() {
        return this.webApplicationType;
    }

    /** @deprecated */
    @Deprecated
    public void setWebEnvironment(boolean webEnvironment) {
        this.webApplicationType = webEnvironment ? WebApplicationType.SERVLET : WebApplicationType.NONE;
    }

    public void setWebApplicationType(WebApplicationType webApplicationType) {
        Assert.notNull(webApplicationType, "WebApplicationType must not be null");
        this.webApplicationType = webApplicationType;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public void setRegisterShutdownHook(boolean registerShutdownHook) {
        this.registerShutdownHook = registerShutdownHook;
    }

    public void setBanner(Banner banner) {
        this.banner = banner;
    }

    public void setBannerMode(Mode bannerMode) {
        this.bannerMode = bannerMode;
    }

    public void setLogStartupInfo(boolean logStartupInfo) {
        this.logStartupInfo = logStartupInfo;
    }

    public void setAddCommandLineProperties(boolean addCommandLineProperties) {
        this.addCommandLineProperties = addCommandLineProperties;
    }

    public void setDefaultProperties(Map<String, Object> defaultProperties) {
        this.defaultProperties = defaultProperties;
    }

    public void setDefaultProperties(Properties defaultProperties) {
        this.defaultProperties = new HashMap();
        Iterator var2 = Collections.list(defaultProperties.propertyNames()).iterator();

        while(var2.hasNext()) {
            Object key = var2.next();
            this.defaultProperties.put((String)key, defaultProperties.get(key));
        }

    }

    public void setAdditionalProfiles(String... profiles) {
        this.additionalProfiles = new LinkedHashSet(Arrays.asList(profiles));
    }

    public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
        this.beanNameGenerator = beanNameGenerator;
    }

    public void setEnvironment(ConfigurableEnvironment environment) {
        this.isCustomEnvironment = true;
        this.environment = environment;
    }

    public void addPrimarySources(Collection<Class<?>> additionalPrimarySources) {
        this.primarySources.addAll(additionalPrimarySources);
    }

    public Set<String> getSources() {
        return this.sources;
    }

    public void setSources(Set<String> sources) {
        Assert.notNull(sources, "Sources must not be null");
        this.sources = new LinkedHashSet(sources);
    }

    public Set<Object> getAllSources() {
        Set<Object> allSources = new LinkedHashSet();
        if (!CollectionUtils.isEmpty(this.primarySources)) {
            allSources.addAll(this.primarySources);
        }

        if (!CollectionUtils.isEmpty(this.sources)) {
            allSources.addAll(this.sources);
        }

        return Collections.unmodifiableSet(allSources);
    }

    public void setResourceLoader(ResourceLoader resourceLoader) {
        Assert.notNull(resourceLoader, "ResourceLoader must not be null");
        this.resourceLoader = resourceLoader;
    }

    public void setApplicationContextClass(Class<? extends ConfigurableApplicationContext> applicationContextClass) {
        this.applicationContextClass = applicationContextClass;
        this.webApplicationType = WebApplicationType.deduceFromApplicationContext(applicationContextClass);
    }

    public void setInitializers(Collection<? extends ApplicationContextInitializer<?>> initializers) {
        this.initializers = new ArrayList();
        this.initializers.addAll(initializers);
    }

    public void addInitializers(ApplicationContextInitializer... initializers) {
        this.initializers.addAll(Arrays.asList(initializers));
    }

    public Set<ApplicationContextInitializer<?>> getInitializers() {
        return asUnmodifiableOrderedSet(this.initializers);
    }

    public void setListeners(Collection<? extends ApplicationListener<?>> listeners) {
        this.listeners = new ArrayList();
        this.listeners.addAll(listeners);
    }

    public void addListeners(ApplicationListener... listeners) {
        this.listeners.addAll(Arrays.asList(listeners));
    }

    public Set<ApplicationListener<?>> getListeners() {
        return asUnmodifiableOrderedSet(this.listeners);
    }

	/*
	 * ??????????????????
	 */
    public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
        return run(new Class[]{primarySource}, args);
    }

    public static ConfigurableApplicationContext run(Class<?>[] primarySources, String[] args) {
        return (new SpringApplication(primarySources)).run(args);
    }

    public static void main(String[] args) throws Exception {
        run(new Class[0], args);
    }

    public static int exit(ApplicationContext context, ExitCodeGenerator... exitCodeGenerators) {
        Assert.notNull(context, "Context must not be null");
        byte exitCode = 0;

        int exitCode;
        try {
            try {
                ExitCodeGenerators generators = new ExitCodeGenerators();
                Collection<ExitCodeGenerator> beans = context.getBeansOfType(ExitCodeGenerator.class).values();
                generators.addAll(exitCodeGenerators);
                generators.addAll(beans);
                exitCode = generators.getExitCode();
                if (exitCode != 0) {
                    context.publishEvent(new ExitCodeEvent(context, exitCode));
                }
            } finally {
                close(context);
            }
        } catch (Exception var9) {
            var9.printStackTrace();
            exitCode = exitCode != 0 ? exitCode : 1;
        }

        return exitCode;
    }

    private static void close(ApplicationContext context) {
        if (context instanceof ConfigurableApplicationContext) {
            ConfigurableApplicationContext closable = (ConfigurableApplicationContext)context;
            closable.close();
        }

    }

    private static <E> Set<E> asUnmodifiableOrderedSet(Collection<E> elements) {
        List<E> list = new ArrayList();
        list.addAll(elements);
        list.sort(AnnotationAwareOrderComparator.INSTANCE);
        return new LinkedHashSet(list);
    }
}
