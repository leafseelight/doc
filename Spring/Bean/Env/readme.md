PropertyResolver
	属性解析
	boolean containsProperty(String var1);
    @Nullable
    String getProperty(String var1);
    String getProperty(String var1, String var2);
    @Nullable
    <T> T getProperty(String var1, Class<T> var2);
    <T> T getProperty(String var1, Class<T> var2, T var3);
    String getRequiredProperty(String var1) throws IllegalStateException;
    <T> T getRequiredProperty(String var1, Class<T> var2) throws IllegalStateException;
    String resolvePlaceholders(String var1);
    String resolveRequiredPlaceholders(String var1) throws IllegalArgumentException;
	
ConfigurablePropertyResolver extends PropertyResolver
	可配置的属性解析

Environment
	环境属性 添加了Profile的概念
	String[] getActiveProfiles();
    String[] getDefaultProfiles();
    @Deprecated
    boolean acceptsProfiles(String... var1);
    boolean acceptsProfiles(Profiles var1);
	
ConfigurableEnvironment	extends Environment, ConfigurablePropertyResolver
	可配置的环境属性
	void setActiveProfiles(String... var1);
    void addActiveProfile(String var1);
    void setDefaultProfiles(String... var1);
    MutablePropertySources getPropertySources();
    Map<String, Object> getSystemProperties();
    Map<String, Object> getSystemEnvironment();
    void merge(ConfigurableEnvironment var1);
	
AbstractEnvironment implements ConfigurableEnvironment
	环境属性基础父类
	spring.profiles.active 指定启动的profiles
	spring.profiles.default 指定默认的profiles
	默认的profile名称为default
	
StandardEnvironment extends AbstractEnvironment	
	标准环境实现
	系统环境存放在名为systemProperties的双层map中
	系统属性存放在名为systemProperties的双层map中
	
StandardServletEnvironment extends StandardEnvironment implements ConfigurableWebEnvironment
	标准servlet环境实现
	保存servletContextInitParams、servletConfigInitParams、jndiProperties