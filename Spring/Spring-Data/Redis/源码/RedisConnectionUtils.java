package org.springframework.data.redis.core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.transaction.support.ResourceHolder;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

// redis连接工具类
public abstract class RedisConnectionUtils {
    private static final Log log = LogFactory.getLog(RedisConnectionUtils.class);

    public RedisConnectionUtils() {
    }

	// 获取连接 并绑定 无事务
    public static RedisConnection bindConnection(RedisConnectionFactory factory) {
        return bindConnection(factory, false);
    }

	// 获取连接 并绑定 有事务
    public static RedisConnection bindConnection(RedisConnectionFactory factory, boolean enableTranactionSupport) {
        return doGetConnection(factory, true, true, enableTranactionSupport);
    }

	// 获取连接 不绑定 无事务
    public static RedisConnection getConnection(RedisConnectionFactory factory) {
        return getConnection(factory, false);
    }

	// 获取连接 不绑定 有事务
    public static RedisConnection getConnection(RedisConnectionFactory factory, boolean enableTranactionSupport) {
        return doGetConnection(factory, true, false, enableTranactionSupport);
    }

	// 获取连接核心方法
    public static RedisConnection doGetConnection(RedisConnectionFactory factory, boolean allowCreate, boolean bind, boolean enableTransactionSupport) {
        Assert.notNull(factory, "No RedisConnectionFactory specified");
        RedisConnectionUtils.RedisConnectionHolder connHolder = (RedisConnectionUtils.RedisConnectionHolder)TransactionSynchronizationManager.getResource(factory);
        if (connHolder != null) {
            if (enableTransactionSupport) {
				// 如果允许事务 要开启事务 并注册到事务管理器
                potentiallyRegisterTransactionSynchronisation(connHolder, factory);
            }

            return connHolder.getConnection();
        } else if (!allowCreate) {
            throw new IllegalArgumentException("No connection found and allowCreate = false");
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Opening RedisConnection");
            }
			// 自行创建连接
            RedisConnection conn = factory.getConnection();
            if (bind) {
                RedisConnection connectionToBind = conn;
                if (enableTransactionSupport && isActualNonReadonlyTransactionActive()) {
                    connectionToBind = createConnectionProxy(conn, factory);
                }

                connHolder = new RedisConnectionUtils.RedisConnectionHolder(connectionToBind);
                TransactionSynchronizationManager.bindResource(factory, connHolder);
                if (enableTransactionSupport) {
                    potentiallyRegisterTransactionSynchronisation(connHolder, factory);
                }

                return connHolder.getConnection();
            } else {
                return conn;
            }
        }
    }

	// 注册事务
    private static void potentiallyRegisterTransactionSynchronisation(RedisConnectionUtils.RedisConnectionHolder connHolder, RedisConnectionFactory factory) {
        if (isActualNonReadonlyTransactionActive() && !connHolder.isTransactionSyncronisationActive()) {
            // 设置事务激活
			connHolder.setTransactionSyncronisationActive(true);
            RedisConnection conn = connHolder.getConnection();
			// 开启事务
            conn.multi();
			// 注册到事务同步管理器上
            TransactionSynchronizationManager.registerSynchronization(new RedisConnectionUtils.RedisTransactionSynchronizer(connHolder, conn, factory));
        }

    }

	// 是否是可写的且事务激活
    private static boolean isActualNonReadonlyTransactionActive() {
        return TransactionSynchronizationManager.isActualTransactionActive() && !TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

	// 创建连接代理
    private static RedisConnection createConnectionProxy(RedisConnection connection, RedisConnectionFactory factory) {
        ProxyFactory proxyFactory = new ProxyFactory(connection);
        proxyFactory.addAdvice(new RedisConnectionUtils.ConnectionSplittingInterceptor(factory));
        return (RedisConnection)RedisConnection.class.cast(proxyFactory.getProxy());
    }

	// 释放连接
    public static void releaseConnection(RedisConnection conn, RedisConnectionFactory factory) {
        if (conn != null) {
            RedisConnectionUtils.RedisConnectionHolder connHolder = (RedisConnectionUtils.RedisConnectionHolder)TransactionSynchronizationManager.getResource(factory);
			// 如果有事务 事务完成时自动释放连接
			if (connHolder != null && connHolder.isTransactionSyncronisationActive()) {
                if (log.isDebugEnabled()) {
                    log.debug("Redis Connection will be closed when transaction finished.");
                }

            } else {
                if (isConnectionTransactional(conn, factory) && TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
					// 如果开启了事务 但是是只读连接 需要解绑并关闭
					unbindConnection(factory);
                } else if (!isConnectionTransactional(conn, factory)) {
                    if (log.isDebugEnabled()) {
                        log.debug("Closing Redis Connection");
                    }

                    conn.close();
                }

            }
        }
    }

	// 解绑
    public static void unbindConnection(RedisConnectionFactory factory) {
        RedisConnectionUtils.RedisConnectionHolder connHolder = (RedisConnectionUtils.RedisConnectionHolder)TransactionSynchronizationManager.unbindResourceIfPossible(factory);
        if (connHolder != null) {
            if (connHolder.isTransactionSyncronisationActive()) {
                if (log.isDebugEnabled()) {
                    log.debug("Redis Connection will be closed when outer transaction finished.");
                }
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Closing bound connection.");
                }

                RedisConnection connection = connHolder.getConnection();
                connection.close();
            }
        }

    }

    public static boolean isConnectionTransactional(RedisConnection conn, RedisConnectionFactory connFactory) {
        if (connFactory == null) {
            return false;
        } else {
            RedisConnectionUtils.RedisConnectionHolder connHolder = (RedisConnectionUtils.RedisConnectionHolder)TransactionSynchronizationManager.getResource(connFactory);
            return connHolder != null && conn == connHolder.getConnection();
        }
    }

	// redis连接容器
    private static class RedisConnectionHolder implements ResourceHolder {
        private boolean unbound;							// 是否解绑
        private final RedisConnection conn;					// 持有的redis连接
        private boolean transactionSyncronisationActive;

        public RedisConnectionHolder(RedisConnection conn) {
            this.conn = conn;
        }

        public boolean isVoid() {
            return this.unbound;
        }

        public RedisConnection getConnection() {
            return this.conn;
        }

        public void reset() {
        }

        public void unbound() {
            this.unbound = true;
        }

        public boolean isTransactionSyncronisationActive() {
            return this.transactionSyncronisationActive;
        }

        public void setTransactionSyncronisationActive(boolean transactionSyncronisationActive) {
            this.transactionSyncronisationActive = transactionSyncronisationActive;
        }
    }

    static class ConnectionSplittingInterceptor implements MethodInterceptor, org.springframework.cglib.proxy.MethodInterceptor {
        private final RedisConnectionFactory factory;

        public ConnectionSplittingInterceptor(RedisConnectionFactory factory) {
            this.factory = factory;
        }

        public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
            RedisCommand commandToExecute = RedisCommand.failsafeCommandLookup(method.getName());
            if (this.isPotentiallyThreadBoundCommand(commandToExecute)) {
                if (RedisConnectionUtils.log.isDebugEnabled()) {
                    RedisConnectionUtils.log.debug(String.format("Invoke '%s' on bound conneciton", method.getName()));
                }

                return this.invoke(method, obj, args);
            } else {
                if (RedisConnectionUtils.log.isDebugEnabled()) {
                    RedisConnectionUtils.log.debug(String.format("Invoke '%s' on unbound conneciton", method.getName()));
                }

                RedisConnection connection = this.factory.getConnection();

                Object var7;
                try {
                    var7 = this.invoke(method, connection, args);
                } finally {
                    if (!connection.isClosed()) {
                        connection.close();
                    }

                }

                return var7;
            }
        }

        private Object invoke(Method method, Object target, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException var5) {
                throw var5.getCause();
            }
        }

        public Object invoke(MethodInvocation invocation) throws Throwable {
            return this.intercept(invocation.getThis(), invocation.getMethod(), invocation.getArguments(), (MethodProxy)null);
        }

        private boolean isPotentiallyThreadBoundCommand(RedisCommand command) {
            return RedisCommand.UNKNOWN.equals(command) || !command.isReadonly();
        }
    }

	// redis事务同步器
    private static class RedisTransactionSynchronizer extends TransactionSynchronizationAdapter {
        private final RedisConnectionUtils.RedisConnectionHolder connHolder;
        private final RedisConnection connection;
        private final RedisConnectionFactory factory;

        private RedisTransactionSynchronizer(RedisConnectionUtils.RedisConnectionHolder connHolder, RedisConnection connection, RedisConnectionFactory factory) {
            this.connHolder = connHolder;
            this.connection = connection;
            this.factory = factory;
        }

        public void afterCompletion(int status) {
            try {
                switch(status) {
                case 0:
                    this.connection.exec();
                    break;
                case 1:
                case 2:
                default:
                    this.connection.discard();
                }
            } finally {
                if (RedisConnectionUtils.log.isDebugEnabled()) {
                    RedisConnectionUtils.log.debug("Closing bound connection after transaction completed with " + status);
                }

                this.connHolder.setTransactionSyncronisationActive(false);
                this.connection.close();
                TransactionSynchronizationManager.unbindResource(this.factory);
            }

        }
    }
}
