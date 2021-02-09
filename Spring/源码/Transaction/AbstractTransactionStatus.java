package org.springframework.transaction.support;

import org.springframework.lang.Nullable;
import org.springframework.transaction.NestedTransactionNotSupportedException;
import org.springframework.transaction.SavepointManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionUsageException;

// 事务状态抽象父类
// 封装了基本的是否回滚的设置查询操作，维护保存点对象 savepoint具体操作由SavepointManager实现来处理
public abstract class AbstractTransactionStatus implements TransactionStatus {
    private boolean rollbackOnly = false;
    private boolean completed = false;
    @Nullable
    private Object savepoint;

    public AbstractTransactionStatus() {
    }

    public void setRollbackOnly() {
        this.rollbackOnly = true;
    }

	// 扩展了全局回滚标识
    public boolean isRollbackOnly() {
        return this.isLocalRollbackOnly() || this.isGlobalRollbackOnly();
    }

    public boolean isLocalRollbackOnly() {
        return this.rollbackOnly;
    }

    public boolean isGlobalRollbackOnly() {
        return false;
    }

    public void setCompleted() {
        this.completed = true;
    }

    public boolean isCompleted() {
        return this.completed;
    }

    public boolean hasSavepoint() {
        return this.savepoint != null;
    }

    protected void setSavepoint(@Nullable Object savepoint) {
        this.savepoint = savepoint;
    }

    @Nullable
    protected Object getSavepoint() {
        return this.savepoint;
    }

    public void createAndHoldSavepoint() throws TransactionException {
        this.setSavepoint(this.getSavepointManager().createSavepoint());
    }

    public void rollbackToHeldSavepoint() throws TransactionException {
        Object savepoint = this.getSavepoint();
        if (savepoint == null) {
            throw new TransactionUsageException("Cannot roll back to savepoint - no savepoint associated with current transaction");
        } else {
            this.getSavepointManager().rollbackToSavepoint(savepoint);
            this.getSavepointManager().releaseSavepoint(savepoint);
            this.setSavepoint((Object)null);
        }
    }

    public void releaseHeldSavepoint() throws TransactionException {
        Object savepoint = this.getSavepoint();
        if (savepoint == null) {
            throw new TransactionUsageException("Cannot release savepoint - no savepoint associated with current transaction");
        } else {
            this.getSavepointManager().releaseSavepoint(savepoint);
            this.setSavepoint((Object)null);
        }
    }

    public Object createSavepoint() throws TransactionException {
        return this.getSavepointManager().createSavepoint();
    }

    public void rollbackToSavepoint(Object savepoint) throws TransactionException {
        this.getSavepointManager().rollbackToSavepoint(savepoint);
    }

    public void releaseSavepoint(Object savepoint) throws TransactionException {
        this.getSavepointManager().releaseSavepoint(savepoint);
    }

    protected SavepointManager getSavepointManager() {
        throw new NestedTransactionNotSupportedException("This transaction does not support savepoints");
    }

    public void flush() {
    }
}
