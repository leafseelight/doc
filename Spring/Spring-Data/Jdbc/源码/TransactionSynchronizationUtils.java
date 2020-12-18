package org.springframework.transaction.support;

import java.util.Iterator;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.core.InfrastructureProxy;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

// 事务同步工具 提取了对TransactionSynchronization列表的操作
public abstract class TransactionSynchronizationUtils {
    private static final Log logger = LogFactory.getLog(TransactionSynchronizationUtils.class);
	// 是否允许aop  为什么通过ScopedObject类是否能加载到来判断 这个类应该是scope代理使用的
    private static final boolean aopAvailable = ClassUtils.isPresent("org.springframework.aop.scope.ScopedObject", TransactionSynchronizationUtils.class.getClassLoader());

    public TransactionSynchronizationUtils() {
    }

	// 判断事务管理器的资源工厂是否是指定的资源工厂
    public static boolean sameResourceFactory(ResourceTransactionManager tm, Object resourceFactory) {
        return unwrapResourceIfNecessary(tm.getResourceFactory()).equals(unwrapResourceIfNecessary(resourceFactory));
    }

    static Object unwrapResourceIfNecessary(Object resource) {
        Assert.notNull(resource, "Resource must not be null");
        Object resourceRef = resource;
		// 如果被代理了 需要解包
        if (resource instanceof InfrastructureProxy) {
            resourceRef = ((InfrastructureProxy)resource).getWrappedObject();
        }

        if (aopAvailable) {
            resourceRef = TransactionSynchronizationUtils.ScopedProxyUnwrapper.unwrapIfNecessary(resourceRef);
        }

        return resourceRef;
    }

	// 触发flush
    public static void triggerFlush() {
        Iterator var0 = TransactionSynchronizationManager.getSynchronizations().iterator();

        while(var0.hasNext()) {
            TransactionSynchronization synchronization = (TransactionSynchronization)var0.next();
            synchronization.flush();
        }

    }

	// 事务提交前触发
    public static void triggerBeforeCommit(boolean readOnly) {
        Iterator var1 = TransactionSynchronizationManager.getSynchronizations().iterator();

        while(var1.hasNext()) {
            TransactionSynchronization synchronization = (TransactionSynchronization)var1.next();
            synchronization.beforeCommit(readOnly);
        }

    }

	// 事务完成前触发
    public static void triggerBeforeCompletion() {
        Iterator var0 = TransactionSynchronizationManager.getSynchronizations().iterator();

        while(var0.hasNext()) {
            TransactionSynchronization synchronization = (TransactionSynchronization)var0.next();

            try {
                synchronization.beforeCompletion();
            } catch (Throwable var3) {
                logger.error("TransactionSynchronization.beforeCompletion threw exception", var3);
            }
        }

    }

	// 事务提交后触发
    public static void triggerAfterCommit() {
        invokeAfterCommit(TransactionSynchronizationManager.getSynchronizations());
    }

    public static void invokeAfterCommit(@Nullable List<TransactionSynchronization> synchronizations) {
        if (synchronizations != null) {
            Iterator var1 = synchronizations.iterator();

            while(var1.hasNext()) {
                TransactionSynchronization synchronization = (TransactionSynchronization)var1.next();
                synchronization.afterCommit();
            }
        }

    }

	// 事务完成后触发
    public static void triggerAfterCompletion(int completionStatus) {
        List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
        invokeAfterCompletion(synchronizations, completionStatus);
    }

    public static void invokeAfterCompletion(@Nullable List<TransactionSynchronization> synchronizations, int completionStatus) {
        if (synchronizations != null) {
            Iterator var2 = synchronizations.iterator();

            while(var2.hasNext()) {
                TransactionSynchronization synchronization = (TransactionSynchronization)var2.next();

                try {
                    synchronization.afterCompletion(completionStatus);
                } catch (Throwable var5) {
                    logger.error("TransactionSynchronization.afterCompletion threw exception", var5);
                }
            }
        }

    }

	// ScopedProxy类型的解包器 ScopedProxy是个特殊的代理类 只封装了被代理类的名称 获取目标类是每次都从bean工厂中获取
    private static class ScopedProxyUnwrapper {
        private ScopedProxyUnwrapper() {
        }

        public static Object unwrapIfNecessary(Object resource) {
			// 如果是ScopedObject代理对象 则从bean工厂中获取
            return resource instanceof ScopedObject ? ((ScopedObject)resource).getTargetObject() : resource;
        }
    }
}
