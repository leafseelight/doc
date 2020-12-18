package org.springframework.jdbc.support;

import javax.sql.DataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

public abstract class JdbcAccessor implements InitializingBean {
    protected final Log logger = LogFactory.getLog(this.getClass());
    @Nullable
    private DataSource dataSource;									// 内部封装了一个数据源
    @Nullable
    private volatile SQLExceptionTranslator exceptionTranslator;	// SQL异常转换器 
    private boolean lazyInit = true;								// 默认懒加载(即使用时创建SQL异常转换器) 

    public JdbcAccessor() {
    }

    public void setDataSource(@Nullable DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Nullable
    public DataSource getDataSource() {
        return this.dataSource;
    }

    protected DataSource obtainDataSource() {
        DataSource dataSource = this.getDataSource();
        Assert.state(dataSource != null, "No DataSource set");
        return dataSource;
    }

    public void setDatabaseProductName(String dbName) {
        this.exceptionTranslator = new SQLErrorCodeSQLExceptionTranslator(dbName);
    }

    public void setExceptionTranslator(SQLExceptionTranslator exceptionTranslator) {
        this.exceptionTranslator = exceptionTranslator;
    }

    public SQLExceptionTranslator getExceptionTranslator() {
        SQLExceptionTranslator exceptionTranslator = this.exceptionTranslator;
        if (exceptionTranslator != null) {
            return exceptionTranslator;
        } else {
            synchronized(this) {
                SQLExceptionTranslator exceptionTranslator = this.exceptionTranslator;
                if (exceptionTranslator == null) {
                    DataSource dataSource = this.getDataSource();
                    if (dataSource != null) {
                        exceptionTranslator = new SQLErrorCodeSQLExceptionTranslator(dataSource);
                    } else {
                        exceptionTranslator = new SQLStateSQLExceptionTranslator();
                    }

                    this.exceptionTranslator = (SQLExceptionTranslator)exceptionTranslator;
                }

                return (SQLExceptionTranslator)exceptionTranslator;
            }
        }
    }

    public void setLazyInit(boolean lazyInit) {
        this.lazyInit = lazyInit;
    }

    public boolean isLazyInit() {
        return this.lazyInit;
    }

	// 初始化完成之后 校验数据源是否已经注入
    public void afterPropertiesSet() {
        if (this.getDataSource() == null) {
            throw new IllegalArgumentException("Property 'dataSource' is required");
        } else {
            if (!this.isLazyInit()) {
                this.getExceptionTranslator();
            }

        }
    }
}
