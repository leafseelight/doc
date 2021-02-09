package org.springframework.transaction.support;

import org.springframework.lang.Nullable;
import org.springframework.transaction.NestedTransactionNotSupportedException;
import org.springframework.transaction.SavepointManager;
import org.springframework.util.Assert;

// 默认的事务状态实现
public class DefaultTransactionStatus extends AbstractTransactionStatus {
    @Nullable
    private final Object transaction;				// 维护了事务状态对象 由于各框架不一样 这里的对象是Object  会委托为具体的对象
    private final boolean newTransaction;			// 是否是新的事务
    private final boolean newSynchronization;		// 是否同步
    private final boolean readOnly;					// 是否只读
    private final boolean debug;			
    @Nullable
    private final Object suspendedResources;

    public DefaultTransactionStatus(@Nullable Object transaction, boolean newTransaction, boolean newSynchronization, boolean readOnly, boolean debug, @Nullable Object suspendedResources) {
        this.transaction = transaction;
        this.newTransaction = newTransaction;
        this.newSynchronization = newSynchronization;
        this.readOnly = readOnly;
        this.debug = debug;
        this.suspendedResources = suspendedResources;
    }

    public Object getTransaction() {
        Assert.state(this.transaction != null, "No transaction active");
        return this.transaction;
    }

    public boolean hasTransaction() {
        return this.transaction != null;
    }

    public boolean isNewTransaction() {
        return this.hasTransaction() && this.newTransaction;
    }

    public boolean isNewSynchronization() {
        return this.newSynchronization;
    }

    public boolean isReadOnly() {
        return this.readOnly;
    }

    public boolean isDebug() {
        return this.debug;
    }

    @Nullable
    public Object getSuspendedResources() {
        return this.suspendedResources;
    }

    public boolean isGlobalRollbackOnly() {
        return this.transaction instanceof SmartTransactionObject && ((SmartTransactionObject)this.transaction).isRollbackOnly();
    }

    protected SavepointManager getSavepointManager() {
        Object transaction = this.transaction;
        if (!(transaction instanceof SavepointManager)) {
            throw new NestedTransactionNotSupportedException("Transaction object [" + this.transaction + "] does not support savepoints");
        } else {
            return (SavepointManager)transaction;
        }
    }

    public boolean isTransactionSavepointManager() {
        return this.transaction instanceof SavepointManager;
    }

    public void flush() {
        if (this.transaction instanceof SmartTransactionObject) {
            ((SmartTransactionObject)this.transaction).flush();
        }

    }
}
