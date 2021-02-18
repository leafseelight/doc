package org.springframework.boot.jdbc;

import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertyNameAliases;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.util.ClassUtils;

public final class DataSourceBuilder<T extends DataSource> {
    private static final String[] DATA_SOURCE_TYPE_NAMES = new String[]{"com.zaxxer.hikari.HikariDataSource", "org.apache.tomcat.jdbc.pool.DataSource", "org.apache.commons.dbcp2.BasicDataSource"};
																// 如果没有指定数据源类型 默认依次查找的数据源
	private Class<? extends DataSource> type;					// 数据源类型
    private ClassLoader classLoader;
    private Map<String, String> properties = new HashMap();		// 数据源属性 url driverClassName username password

    public static DataSourceBuilder<?> create() {
        return new DataSourceBuilder((ClassLoader)null);
    }

    public static DataSourceBuilder<?> create(ClassLoader classLoader) {
        return new DataSourceBuilder(classLoader);
    }

    private DataSourceBuilder(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

	// 构建数据源
    public T build() {
        Class<? extends DataSource> type = this.getType();
        DataSource result = (DataSource)BeanUtils.instantiateClass(type);	// 初始化数据源对象
        this.maybeGetDriverClassName();
        this.bind(result);
        return result;
    }

	// 如果没有driverClassName但是有url 则通过url解析出driverClassName
    private void maybeGetDriverClassName() {
        if(!this.properties.containsKey("driverClassName") && this.properties.containsKey("url")) {
            String url = (String)this.properties.get("url");
            String driverClass = DatabaseDriver.fromJdbcUrl(url).getDriverClassName();
            this.properties.put("driverClassName", driverClass);
        }

    }

	// 绑定资源
    private void bind(DataSource result) {
        ConfigurationPropertySource source = new MapConfigurationPropertySource(this.properties);
        ConfigurationPropertyNameAliases aliases = new ConfigurationPropertyNameAliases();
        aliases.addAliases("url", new String[]{"jdbc-url"});
        aliases.addAliases("username", new String[]{"user"});
        Binder binder = new Binder(new ConfigurationPropertySource[]{source.withAliases(aliases)});
        binder.bind(ConfigurationPropertyName.EMPTY, Bindable.ofInstance(result));
    }

    public <D extends DataSource> DataSourceBuilder<D> type(Class<D> type) {
        this.type = type;
        return this;
    }

    public DataSourceBuilder<T> url(String url) {
        this.properties.put("url", url);
        return this;
    }

    public DataSourceBuilder<T> driverClassName(String driverClassName) {
        this.properties.put("driverClassName", driverClassName);
        return this;
    }

    public DataSourceBuilder<T> username(String username) {
        this.properties.put("username", username);
        return this;
    }

    public DataSourceBuilder<T> password(String password) {
        this.properties.put("password", password);
        return this;
    }

    public static Class<? extends DataSource> findType(ClassLoader classLoader) {
        String[] var1 = DATA_SOURCE_TYPE_NAMES;
        int var2 = var1.length;
        int var3 = 0;

        while(var3 < var2) {
            String name = var1[var3];

            try {
                return ClassUtils.forName(name, classLoader);
            } catch (Exception var6) {
                ++var3;
            }
        }

        return null;
    }

    private Class<? extends DataSource> getType() {
        Class<? extends DataSource> type = this.type != null?this.type:findType(this.classLoader);
        if(type != null) {
            return type;
        } else {
            throw new IllegalStateException("No supported DataSource type found");
        }
    }
}
