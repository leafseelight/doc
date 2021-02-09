package org.springframework.transaction.interceptor;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Properties;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;

// 事务拦截器
public class TransactionInterceptor extends TransactionAspectSupport implements MethodInterceptor, Serializable {
    public TransactionInterceptor() {
    }

    public TransactionInterceptor(TransactionManager ptm, TransactionAttributeSource tas) {
        this.setTransactionManager(ptm);
        this.setTransactionAttributeSource(tas);
    }

    /** @deprecated */
    @Deprecated
    public TransactionInterceptor(PlatformTransactionManager ptm, TransactionAttributeSource tas) {
        this.setTransactionManager(ptm);
        this.setTransactionAttributeSource(tas);
    }

    /** @deprecated */
    @Deprecated
    public TransactionInterceptor(PlatformTransactionManager ptm, Properties attributes) {
        this.setTransactionManager(ptm);
        this.setTransactionAttributes(attributes);
    }

	// 事务拦截器入口
    @Nullable
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Class<?> targetClass = invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null;
        Method var10001 = invocation.getMethod();
        invocation.getClass();
        return this.invokeWithinTransaction(var10001, targetClass, invocation::proceed);
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.defaultWriteObject();
        oos.writeObject(this.getTransactionManagerBeanName());
        oos.writeObject(this.getTransactionManager());
        oos.writeObject(this.getTransactionAttributeSource());
        oos.writeObject(this.getBeanFactory());
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        this.setTransactionManagerBeanName((String)ois.readObject());
        this.setTransactionManager((PlatformTransactionManager)ois.readObject());
        this.setTransactionAttributeSource((TransactionAttributeSource)ois.readObject());
        this.setBeanFactory((BeanFactory)ois.readObject());
    }
}
