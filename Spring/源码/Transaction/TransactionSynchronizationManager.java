package org.springframework.transaction.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

// 事务同步管理器
public abstract class TransactionSynchronizationManager {
    private static final Log logger = LogFactory.getLog(TransactionSynchronizationManager.class);
	// 数据源与连接的映射
    private static final ThreadLocal<Map<Object, Object>> resources = new NamedThreadLocal("Transactional resources");
	// 事务同步集合
    private static final ThreadLocal<Set<TransactionSynchronization>> synchronizations = new NamedThreadLocal("Transaction synchronizations");
    // 当前事务名称
	private static final ThreadLocal<String> currentTransactionName = new NamedThreadLocal("Current transaction name");
    // 当前事务是否只读
	private static final ThreadLocal<Boolean> currentTransactionReadOnly = new NamedThreadLocal("Current transaction read-only status");
    // 当前事务隔离级别
	private static final ThreadLocal<Integer> currentTransactionIsolationLevel = new NamedThreadLocal("Current transaction isolation level");
    // 当前事务是否激活 用于TransactionSynchronization初始化标记
	private static final ThreadLocal<Boolean> actualTransactionActive = new NamedThreadLocal("Actual transaction active");

    public TransactionSynchronizationManager() {
    }

	// 获取资源map
    public static Map<Object, Object> getResourceMap() {
        Map<Object, Object> map = (Map)resources.get();
        return map != null ? Collections.unmodifiableMap(map) : Collections.emptyMap();
    }

	// 判断资源是否存在
    public static boolean hasResource(Object key) {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Object value = doGetResource(actualKey);
        return value != null;
    }

	// 获取资源 
    @Nullable
    public static Object getResource(Object key) {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Object value = doGetResource(actualKey);
        if (value != null && logger.isTraceEnabled()) {
            logger.trace("Retrieved value [" + value + "] for key [" + actualKey + "] bound to thread [" + Thread.currentThread().getName() + "]");
        }

        return value;
    }

    @Nullable
    private static Object doGetResource(Object actualKey) {
        Map<Object, Object> map = (Map)resources.get();
        if (map == null) {
            return null;
        } else {
            Object value = map.get(actualKey);
            if (value instanceof ResourceHolder && ((ResourceHolder)value).isVoid()) {
                map.remove(actualKey);
                if (map.isEmpty()) {
                    resources.remove();
                }

                value = null;
            }

            return value;
        }
    }

	// 绑定资源
    public static void bindResource(Object key, Object value) throws IllegalStateException {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Assert.notNull(value, "Value must not be null");
        Map<Object, Object> map = (Map)resources.get();
        if (map == null) {
            map = new HashMap();
            resources.set(map);
        }

        Object oldValue = ((Map)map).put(actualKey, value);
        if (oldValue instanceof ResourceHolder && ((ResourceHolder)oldValue).isVoid()) {
            oldValue = null;
        }

        if (oldValue != null) {
            throw new IllegalStateException("Already value [" + oldValue + "] for key [" + actualKey + "] bound to thread [" + Thread.currentThread().getName() + "]");
        } else {
            if (logger.isTraceEnabled()) {
                logger.trace("Bound value [" + value + "] for key [" + actualKey + "] to thread [" + Thread.currentThread().getName() + "]");
            }

        }
    }

	// 解绑资源
    public static Object unbindResource(Object key) throws IllegalStateException {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        Object value = doUnbindResource(actualKey);
        if (value == null) {
            throw new IllegalStateException("No value for key [" + actualKey + "] bound to thread [" + Thread.currentThread().getName() + "]");
        } else {
            return value;
        }
    }

    @Nullable
    public static Object unbindResourceIfPossible(Object key) {
        Object actualKey = TransactionSynchronizationUtils.unwrapResourceIfNecessary(key);
        return doUnbindResource(actualKey);
    }

    @Nullable
    private static Object doUnbindResource(Object actualKey) {
        Map<Object, Object> map = (Map)resources.get();
        if (map == null) {
            return null;
        } else {
            Object value = map.remove(actualKey);
            if (map.isEmpty()) {
                resources.remove();
            }

            if (value instanceof ResourceHolder && ((ResourceHolder)value).isVoid()) {
                value = null;
            }

            if (value != null && logger.isTraceEnabled()) {
                logger.trace("Removed value [" + value + "] for key [" + actualKey + "] from thread [" + Thread.currentThread().getName() + "]");
            }

            return value;
        }
    }

	// 事务同步队列是否激活
    public static boolean isSynchronizationActive() {
        return synchronizations.get() != null;
    }

	// 初始化Synchronization 就是创建一个空的有序集合
    public static void initSynchronization() throws IllegalStateException {
        if (isSynchronizationActive()) {
            throw new IllegalStateException("Cannot activate transaction synchronization - already active");
        } else {
            logger.trace("Initializing transaction synchronization");
            synchronizations.set(new LinkedHashSet());
        }
    }

	// 注册Synchronization 就是往Synchronization集合中添加新的元素
    public static void registerSynchronization(TransactionSynchronization synchronization) throws IllegalStateException {
        Assert.notNull(synchronization, "TransactionSynchronization must not be null");
        Set<TransactionSynchronization> synchs = (Set)synchronizations.get();
        if (synchs == null) {
            throw new IllegalStateException("Transaction synchronization is not active");
        } else {
            synchs.add(synchronization);
        }
    }

	// 获取事务同步集合
    public static List<TransactionSynchronization> getSynchronizations() throws IllegalStateException {
        Set<TransactionSynchronization> synchs = (Set)synchronizations.get();
        if (synchs == null) {
            throw new IllegalStateException("Transaction synchronization is not active");
        } else if (synchs.isEmpty()) {
            return Collections.emptyList();
        } else {
            List<TransactionSynchronization> sortedSynchs = new ArrayList(synchs);
            AnnotationAwareOrderComparator.sort(sortedSynchs);  // 按照@Priority排序
            return Collections.unmodifiableList(sortedSynchs);
        }
    }

	// 清空事务同步集合
    public static void clearSynchronization() throws IllegalStateException {
        if (!isSynchronizationActive()) {
            throw new IllegalStateException("Cannot deactivate transaction synchronization - not active");
        } else {
            logger.trace("Clearing transaction synchronization");
            synchronizations.remove();
        }
    }

    public static void setCurrentTransactionName(@Nullable String name) {
        currentTransactionName.set(name);
    }

    @Nullable
    public static String getCurrentTransactionName() {
        return (String)currentTransactionName.get();
    }

    public static void setCurrentTransactionReadOnly(boolean readOnly) {
        currentTransactionReadOnly.set(readOnly ? Boolean.TRUE : null);
    }

    public static boolean isCurrentTransactionReadOnly() {
        return currentTransactionReadOnly.get() != null;
    }

    public static void setCurrentTransactionIsolationLevel(@Nullable Integer isolationLevel) {
        currentTransactionIsolationLevel.set(isolationLevel);
    }

    @Nullable
    public static Integer getCurrentTransactionIsolationLevel() {
        return (Integer)currentTransactionIsolationLevel.get();
    }

    public static void setActualTransactionActive(boolean active) {
        actualTransactionActive.set(active ? Boolean.TRUE : null);
    }

    public static boolean isActualTransactionActive() {
        return actualTransactionActive.get() != null;
    }

    public static void clear() {
        synchronizations.remove();
        currentTransactionName.remove();
        currentTransactionReadOnly.remove();
        currentTransactionIsolationLevel.remove();
        actualTransactionActive.remove();
    }
}
