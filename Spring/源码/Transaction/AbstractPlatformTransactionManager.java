package org.springframework.transaction.support;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Constants;
import org.springframework.lang.Nullable;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.InvalidTimeoutException;
import org.springframework.transaction.NestedTransactionNotSupportedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSuspensionNotSupportedException;
import org.springframework.transaction.UnexpectedRollbackException;

public abstract class AbstractPlatformTransactionManager implements PlatformTransactionManager, Serializable {
    public static final int SYNCHRONIZATION_ALWAYS = 0;
    public static final int SYNCHRONIZATION_ON_ACTUAL_TRANSACTION = 1;
    public static final int SYNCHRONIZATION_NEVER = 2;
    private static final Constants constants = new Constants(AbstractPlatformTransactionManager.class);	// 把AbstractPlatformTransactionManager的字段及字段值放到一个map中
    protected transient Log logger = LogFactory.getLog(this.getClass());
    private int transactionSynchronization = 0;
    private int defaultTimeout = -1;
    private boolean nestedTransactionAllowed = false;				// 是否允许嵌套事务
    private boolean validateExistingTransaction = false;			// 是否校验已存在事务
    private boolean globalRollbackOnParticipationFailure = true;	// 失败时是否全局回滚
    private boolean failEarlyOnGlobalRollbackOnly = false;
    private boolean rollbackOnCommitFailure = false;

    public AbstractPlatformTransactionManager() {
    }

    public final void setTransactionSynchronizationName(String constantName) {
        this.setTransactionSynchronization(constants.asNumber(constantName).intValue());
    }

    public final void setTransactionSynchronization(int transactionSynchronization) {
        this.transactionSynchronization = transactionSynchronization;
    }

    public final int getTransactionSynchronization() {
        return this.transactionSynchronization;
    }

    public final void setDefaultTimeout(int defaultTimeout) {
        if (defaultTimeout < -1) {
            throw new InvalidTimeoutException("Invalid default timeout", defaultTimeout);
        } else {
            this.defaultTimeout = defaultTimeout;
        }
    }

    public final int getDefaultTimeout() {
        return this.defaultTimeout;
    }

    public final void setNestedTransactionAllowed(boolean nestedTransactionAllowed) {
        this.nestedTransactionAllowed = nestedTransactionAllowed;
    }

    public final boolean isNestedTransactionAllowed() {
        return this.nestedTransactionAllowed;
    }

    public final void setValidateExistingTransaction(boolean validateExistingTransaction) {
        this.validateExistingTransaction = validateExistingTransaction;
    }

    public final boolean isValidateExistingTransaction() {
        return this.validateExistingTransaction;
    }

    public final void setGlobalRollbackOnParticipationFailure(boolean globalRollbackOnParticipationFailure) {
        this.globalRollbackOnParticipationFailure = globalRollbackOnParticipationFailure;
    }

    public final boolean isGlobalRollbackOnParticipationFailure() {
        return this.globalRollbackOnParticipationFailure;
    }

    public final void setFailEarlyOnGlobalRollbackOnly(boolean failEarlyOnGlobalRollbackOnly) {
        this.failEarlyOnGlobalRollbackOnly = failEarlyOnGlobalRollbackOnly;
    }

    public final boolean isFailEarlyOnGlobalRollbackOnly() {
        return this.failEarlyOnGlobalRollbackOnly;
    }

    public final void setRollbackOnCommitFailure(boolean rollbackOnCommitFailure) {
        this.rollbackOnCommitFailure = rollbackOnCommitFailure;
    }

    public final boolean isRollbackOnCommitFailure() {
        return this.rollbackOnCommitFailure;
    }

	// 获取事务状态 【核心方法】
    public final TransactionStatus getTransaction(@Nullable TransactionDefinition definition) throws TransactionException {
        TransactionDefinition def = definition != null ? definition : TransactionDefinition.withDefaults();
		// 获取事务对象 子类实现
        Object transaction = this.doGetTransaction();
        boolean debugEnabled = this.logger.isDebugEnabled();
        if (this.isExistingTransaction(transaction)) {
			// 处理已经存在的事务
            return this.handleExistingTransaction(def, transaction, debugEnabled);
        } else if (def.getTimeout() < -1) {
            throw new InvalidTimeoutException("Invalid transaction timeout", def.getTimeout());
        } else if (def.getPropagationBehavior() == 2) {
			// 如果当前不存在事务 但是传播机制是PROPAGATION_MANDATORY 则抛出异常
            throw new IllegalTransactionStateException("No existing transaction found for transaction marked with propagation 'mandatory'");
        } else if (def.getPropagationBehavior() != 0 && def.getPropagationBehavior() != 3 && def.getPropagationBehavior() != 6) {
            if (def.getIsolationLevel() != -1 && this.logger.isWarnEnabled()) {
                this.logger.warn("Custom isolation level specified but no actual transaction initiated; isolation level will effectively be ignored: " + def);
            }

            boolean newSynchronization = this.getTransactionSynchronization() == 0;
            return this.prepareTransactionStatus(def, (Object)null, true, newSynchronization, debugEnabled, (Object)null);
        } else {
            AbstractPlatformTransactionManager.SuspendedResourcesHolder suspendedResources = this.suspend((Object)null);
            if (debugEnabled) {
                this.logger.debug("Creating new transaction with name [" + def.getName() + "]: " + def);
            }

            try {
                return this.startTransaction(def, transaction, debugEnabled, suspendedResources);
            } catch (Error | RuntimeException var7) {
                this.resume((Object)null, suspendedResources);
                throw var7;
            }
        }
    }

    private TransactionStatus startTransaction(TransactionDefinition definition, Object transaction, boolean debugEnabled, @Nullable AbstractPlatformTransactionManager.SuspendedResourcesHolder suspendedResources) {
        boolean newSynchronization = this.getTransactionSynchronization() != 2;
        DefaultTransactionStatus status = this.newTransactionStatus(definition, transaction, true, newSynchronization, debugEnabled, suspendedResources);
        this.doBegin(transaction, definition);
        this.prepareSynchronization(status, definition);
        return status;
    }

	// 处理已经存在的事务
    private TransactionStatus handleExistingTransaction(TransactionDefinition definition, Object transaction, boolean debugEnabled) throws TransactionException {
        if (definition.getPropagationBehavior() == 5) {
			// PROPAGATION_NEVER 以非事务方式运行 如果存在事务则会抛出异常
            throw new IllegalTransactionStateException("Existing transaction found for transaction marked with propagation 'never'");
        } else {
            AbstractPlatformTransactionManager.SuspendedResourcesHolder suspendedResources;
            if (definition.getPropagationBehavior() == 4) {
				// PROPAGATION_NOT_SUPPORTED 以非事务方式运行 如果当前存在事务 则挂起当前事务
                if (debugEnabled) {
                    this.logger.debug("Suspending current transaction");
                }
				// 挂起事务
                suspendedResources = this.suspend(transaction);
                boolean newSynchronization = this.getTransactionSynchronization() == 0;
                return this.prepareTransactionStatus(definition, (Object)null, false, newSynchronization, debugEnabled, suspendedResources);
            } else if (definition.getPropagationBehavior() == 3) {
                if (debugEnabled) {
                    this.logger.debug("Suspending current transaction, creating new transaction with name [" + definition.getName() + "]");
                }

                suspendedResources = this.suspend(transaction);

                try {
                    return this.startTransaction(definition, transaction, debugEnabled, suspendedResources);
                } catch (Error | RuntimeException var6) {
                    this.resumeAfterBeginException(transaction, suspendedResources, var6);
                    throw var6;
                }
            } else if (definition.getPropagationBehavior() == 6) {
                if (!this.isNestedTransactionAllowed()) {
                    throw new NestedTransactionNotSupportedException("Transaction manager does not allow nested transactions by default - specify 'nestedTransactionAllowed' property with value 'true'");
                } else {
                    if (debugEnabled) {
                        this.logger.debug("Creating nested transaction with name [" + definition.getName() + "]");
                    }

                    if (this.useSavepointForNestedTransaction()) {
                        DefaultTransactionStatus status = this.prepareTransactionStatus(definition, transaction, false, false, debugEnabled, (Object)null);
                        status.createAndHoldSavepoint();
                        return status;
                    } else {
                        return this.startTransaction(definition, transaction, debugEnabled, (AbstractPlatformTransactionManager.SuspendedResourcesHolder)null);
                    }
                }
            } else {
                if (debugEnabled) {
                    this.logger.debug("Participating in existing transaction");
                }

                if (this.isValidateExistingTransaction()) {
                    if (definition.getIsolationLevel() != -1) {
                        Integer currentIsolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
                        if (currentIsolationLevel == null || currentIsolationLevel != definition.getIsolationLevel()) {
                            Constants isoConstants = DefaultTransactionDefinition.constants;
                            throw new IllegalTransactionStateException("Participating transaction with definition [" + definition + "] specifies isolation level which is incompatible with existing transaction: " + (currentIsolationLevel != null ? isoConstants.toCode(currentIsolationLevel, "ISOLATION_") : "(unknown)"));
                        }
                    }

                    if (!definition.isReadOnly() && TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
                        throw new IllegalTransactionStateException("Participating transaction with definition [" + definition + "] is not marked as read-only but existing transaction is");
                    }
                }

                boolean newSynchronization = this.getTransactionSynchronization() != 2;
                return this.prepareTransactionStatus(definition, transaction, false, newSynchronization, debugEnabled, (Object)null);
            }
        }
    }

    protected final DefaultTransactionStatus prepareTransactionStatus(TransactionDefinition definition, @Nullable Object transaction, boolean newTransaction, boolean newSynchronization, boolean debug, @Nullable Object suspendedResources) {
        DefaultTransactionStatus status = this.newTransactionStatus(definition, transaction, newTransaction, newSynchronization, debug, suspendedResources);
        this.prepareSynchronization(status, definition);
        return status;
    }

    protected DefaultTransactionStatus newTransactionStatus(TransactionDefinition definition, @Nullable Object transaction, boolean newTransaction, boolean newSynchronization, boolean debug, @Nullable Object suspendedResources) {
        boolean actualNewSynchronization = newSynchronization && !TransactionSynchronizationManager.isSynchronizationActive();
        return new DefaultTransactionStatus(transaction, newTransaction, actualNewSynchronization, definition.isReadOnly(), debug, suspendedResources);
    }

    protected void prepareSynchronization(DefaultTransactionStatus status, TransactionDefinition definition) {
        if (status.isNewSynchronization()) {
            TransactionSynchronizationManager.setActualTransactionActive(status.hasTransaction());
            TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(definition.getIsolationLevel() != -1 ? definition.getIsolationLevel() : null);
            TransactionSynchronizationManager.setCurrentTransactionReadOnly(definition.isReadOnly());
            TransactionSynchronizationManager.setCurrentTransactionName(definition.getName());
            TransactionSynchronizationManager.initSynchronization();
        }

    }

    protected int determineTimeout(TransactionDefinition definition) {
        return definition.getTimeout() != -1 ? definition.getTimeout() : this.getDefaultTimeout();
    }

	// 挂起事务
    @Nullable
    protected final AbstractPlatformTransactionManager.SuspendedResourcesHolder suspend(@Nullable Object transaction) throws TransactionException {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
			// 事务同步有序集合已激活 进行挂起事务操作 ？先清空事务同步集合？
            List suspendedSynchronizations = this.doSuspendSynchronization();

            try {
                Object suspendedResources = null;
                if (transaction != null) {
					// 挂起当前事务的资源
                    suspendedResources = this.doSuspend(transaction);
                }
				// 清空绑定在本地线程的一些变量：事务名称 是否只读 隔离级别等
                String name = TransactionSynchronizationManager.getCurrentTransactionName();
                TransactionSynchronizationManager.setCurrentTransactionName((String)null);
                boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
                TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
                Integer isolationLevel = TransactionSynchronizationManager.getCurrentTransactionIsolationLevel();
                TransactionSynchronizationManager.setCurrentTransactionIsolationLevel((Integer)null);
                boolean wasActive = TransactionSynchronizationManager.isActualTransactionActive();
                TransactionSynchronizationManager.setActualTransactionActive(false);
                return new AbstractPlatformTransactionManager.SuspendedResourcesHolder(suspendedResources, suspendedSynchronizations, name, readOnly, isolationLevel, wasActive);
            } catch (Error | RuntimeException var8) {
                this.doResumeSynchronization(suspendedSynchronizations);
                throw var8;
            }
        } else if (transaction != null) {
            Object suspendedResources = this.doSuspend(transaction);
            return new AbstractPlatformTransactionManager.SuspendedResourcesHolder(suspendedResources);
        } else {
            return null;
        }
    }

    protected final void resume(@Nullable Object transaction, @Nullable AbstractPlatformTransactionManager.SuspendedResourcesHolder resourcesHolder) throws TransactionException {
        if (resourcesHolder != null) {
            Object suspendedResources = resourcesHolder.suspendedResources;
            if (suspendedResources != null) {
                this.doResume(transaction, suspendedResources);
            }

            List<TransactionSynchronization> suspendedSynchronizations = resourcesHolder.suspendedSynchronizations;
            if (suspendedSynchronizations != null) {
                TransactionSynchronizationManager.setActualTransactionActive(resourcesHolder.wasActive);
                TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(resourcesHolder.isolationLevel);
                TransactionSynchronizationManager.setCurrentTransactionReadOnly(resourcesHolder.readOnly);
                TransactionSynchronizationManager.setCurrentTransactionName(resourcesHolder.name);
                this.doResumeSynchronization(suspendedSynchronizations);
            }
        }

    }

    private void resumeAfterBeginException(Object transaction, @Nullable AbstractPlatformTransactionManager.SuspendedResourcesHolder suspendedResources, Throwable beginEx) {
        try {
            this.resume(transaction, suspendedResources);
        } catch (Error | RuntimeException var6) {
            String exMessage = "Inner transaction begin exception overridden by outer transaction resume exception";
            this.logger.error(exMessage, beginEx);
            throw var6;
        }
    }

	// 挂起事务
    private List<TransactionSynchronization> doSuspendSynchronization() {
		// 获取存在本地变量中的事务同步集合
        List<TransactionSynchronization> suspendedSynchronizations = TransactionSynchronizationManager.getSynchronizations();
        Iterator var2 = suspendedSynchronizations.iterator();

        while(var2.hasNext()) {
            TransactionSynchronization synchronization = (TransactionSynchronization)var2.next();
            synchronization.suspend();  // 挂起事务 挂起逻辑由TransactionSynchronization的子类实现
        }

        TransactionSynchronizationManager.clearSynchronization(); // 清空本地变量中的事务同步集合
        return suspendedSynchronizations;
    }

    private void doResumeSynchronization(List<TransactionSynchronization> suspendedSynchronizations) {
        TransactionSynchronizationManager.initSynchronization();
        Iterator var2 = suspendedSynchronizations.iterator();

        while(var2.hasNext()) {
            TransactionSynchronization synchronization = (TransactionSynchronization)var2.next();
            synchronization.resume();
            TransactionSynchronizationManager.registerSynchronization(synchronization);
        }

    }

    public final void commit(TransactionStatus status) throws TransactionException {
        if (status.isCompleted()) {
            throw new IllegalTransactionStateException("Transaction is already completed - do not call commit or rollback more than once per transaction");
        } else {
            DefaultTransactionStatus defStatus = (DefaultTransactionStatus)status;
            if (defStatus.isLocalRollbackOnly()) {
                if (defStatus.isDebug()) {
                    this.logger.debug("Transactional code has requested rollback");
                }

                this.processRollback(defStatus, false);
            } else if (!this.shouldCommitOnGlobalRollbackOnly() && defStatus.isGlobalRollbackOnly()) {
                if (defStatus.isDebug()) {
                    this.logger.debug("Global transaction is marked as rollback-only but transactional code requested commit");
                }

                this.processRollback(defStatus, true);
            } else {
                this.processCommit(defStatus);
            }
        }
    }

    private void processCommit(DefaultTransactionStatus status) throws TransactionException {
        try {
            boolean beforeCompletionInvoked = false;

            try {
                boolean unexpectedRollback = false;
                this.prepareForCommit(status);
                this.triggerBeforeCommit(status);
                this.triggerBeforeCompletion(status);
                beforeCompletionInvoked = true;
                if (status.hasSavepoint()) {
                    if (status.isDebug()) {
                        this.logger.debug("Releasing transaction savepoint");
                    }

                    unexpectedRollback = status.isGlobalRollbackOnly();
                    status.releaseHeldSavepoint();
                } else if (status.isNewTransaction()) {
                    if (status.isDebug()) {
                        this.logger.debug("Initiating transaction commit");
                    }

                    unexpectedRollback = status.isGlobalRollbackOnly();
                    this.doCommit(status);
                } else if (this.isFailEarlyOnGlobalRollbackOnly()) {
                    unexpectedRollback = status.isGlobalRollbackOnly();
                }

                if (unexpectedRollback) {
                    throw new UnexpectedRollbackException("Transaction silently rolled back because it has been marked as rollback-only");
                }
            } catch (UnexpectedRollbackException var17) {
                this.triggerAfterCompletion(status, 1);
                throw var17;
            } catch (TransactionException var18) {
                if (this.isRollbackOnCommitFailure()) {
                    this.doRollbackOnCommitException(status, var18);
                } else {
                    this.triggerAfterCompletion(status, 2);
                }

                throw var18;
            } catch (Error | RuntimeException var19) {
                if (!beforeCompletionInvoked) {
                    this.triggerBeforeCompletion(status);
                }

                this.doRollbackOnCommitException(status, var19);
                throw var19;
            }

            try {
                this.triggerAfterCommit(status);
            } finally {
                this.triggerAfterCompletion(status, 0);
            }
        } finally {
            this.cleanupAfterCompletion(status);
        }

    }

    public final void rollback(TransactionStatus status) throws TransactionException {
        if (status.isCompleted()) {
            throw new IllegalTransactionStateException("Transaction is already completed - do not call commit or rollback more than once per transaction");
        } else {
            DefaultTransactionStatus defStatus = (DefaultTransactionStatus)status;
            this.processRollback(defStatus, false);
        }
    }

    private void processRollback(DefaultTransactionStatus status, boolean unexpected) {
        try {
            boolean unexpectedRollback = unexpected;

            try {
                this.triggerBeforeCompletion(status);
                if (status.hasSavepoint()) {
                    if (status.isDebug()) {
                        this.logger.debug("Rolling back transaction to savepoint");
                    }

                    status.rollbackToHeldSavepoint();
                } else if (status.isNewTransaction()) {
                    if (status.isDebug()) {
                        this.logger.debug("Initiating transaction rollback");
                    }

                    this.doRollback(status);
                } else {
                    if (status.hasTransaction()) {
                        if (!status.isLocalRollbackOnly() && !this.isGlobalRollbackOnParticipationFailure()) {
                            if (status.isDebug()) {
                                this.logger.debug("Participating transaction failed - letting transaction originator decide on rollback");
                            }
                        } else {
                            if (status.isDebug()) {
                                this.logger.debug("Participating transaction failed - marking existing transaction as rollback-only");
                            }

                            this.doSetRollbackOnly(status);
                        }
                    } else {
                        this.logger.debug("Should roll back transaction but cannot - no transaction available");
                    }

                    if (!this.isFailEarlyOnGlobalRollbackOnly()) {
                        unexpectedRollback = false;
                    }
                }
            } catch (Error | RuntimeException var8) {
                this.triggerAfterCompletion(status, 2);
                throw var8;
            }

            this.triggerAfterCompletion(status, 1);
            if (unexpectedRollback) {
                throw new UnexpectedRollbackException("Transaction rolled back because it has been marked as rollback-only");
            }
        } finally {
            this.cleanupAfterCompletion(status);
        }

    }

    private void doRollbackOnCommitException(DefaultTransactionStatus status, Throwable ex) throws TransactionException {
        try {
            if (status.isNewTransaction()) {
                if (status.isDebug()) {
                    this.logger.debug("Initiating transaction rollback after commit exception", ex);
                }

                this.doRollback(status);
            } else if (status.hasTransaction() && this.isGlobalRollbackOnParticipationFailure()) {
                if (status.isDebug()) {
                    this.logger.debug("Marking existing transaction as rollback-only after commit exception", ex);
                }

                this.doSetRollbackOnly(status);
            }
        } catch (Error | RuntimeException var4) {
            this.logger.error("Commit exception overridden by rollback exception", ex);
            this.triggerAfterCompletion(status, 2);
            throw var4;
        }

        this.triggerAfterCompletion(status, 1);
    }

    protected final void triggerBeforeCommit(DefaultTransactionStatus status) {
        if (status.isNewSynchronization()) {
            if (status.isDebug()) {
                this.logger.trace("Triggering beforeCommit synchronization");
            }

            TransactionSynchronizationUtils.triggerBeforeCommit(status.isReadOnly());
        }

    }

    protected final void triggerBeforeCompletion(DefaultTransactionStatus status) {
        if (status.isNewSynchronization()) {
            if (status.isDebug()) {
                this.logger.trace("Triggering beforeCompletion synchronization");
            }

            TransactionSynchronizationUtils.triggerBeforeCompletion();
        }

    }

    private void triggerAfterCommit(DefaultTransactionStatus status) {
        if (status.isNewSynchronization()) {
            if (status.isDebug()) {
                this.logger.trace("Triggering afterCommit synchronization");
            }

            TransactionSynchronizationUtils.triggerAfterCommit();
        }

    }

    private void triggerAfterCompletion(DefaultTransactionStatus status, int completionStatus) {
        if (status.isNewSynchronization()) {
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            TransactionSynchronizationManager.clearSynchronization();
            if (status.hasTransaction() && !status.isNewTransaction()) {
                if (!synchronizations.isEmpty()) {
                    this.registerAfterCompletionWithExistingTransaction(status.getTransaction(), synchronizations);
                }
            } else {
                if (status.isDebug()) {
                    this.logger.trace("Triggering afterCompletion synchronization");
                }

                this.invokeAfterCompletion(synchronizations, completionStatus);
            }
        }

    }

    protected final void invokeAfterCompletion(List<TransactionSynchronization> synchronizations, int completionStatus) {
        TransactionSynchronizationUtils.invokeAfterCompletion(synchronizations, completionStatus);
    }

    private void cleanupAfterCompletion(DefaultTransactionStatus status) {
        status.setCompleted();
        if (status.isNewSynchronization()) {
            TransactionSynchronizationManager.clear();
        }

        if (status.isNewTransaction()) {
            this.doCleanupAfterCompletion(status.getTransaction());
        }

        if (status.getSuspendedResources() != null) {
            if (status.isDebug()) {
                this.logger.debug("Resuming suspended transaction after completion of inner transaction");
            }

            Object transaction = status.hasTransaction() ? status.getTransaction() : null;
            this.resume(transaction, (AbstractPlatformTransactionManager.SuspendedResourcesHolder)status.getSuspendedResources());
        }

    }

    protected abstract Object doGetTransaction() throws TransactionException;

	// 是否是已经存在的事务 子类实现
    protected boolean isExistingTransaction(Object transaction) throws TransactionException {
        return false;
    }

    protected boolean useSavepointForNestedTransaction() {
        return true;
    }

    protected abstract void doBegin(Object var1, TransactionDefinition var2) throws TransactionException;

	// 挂起当前资源 子类实现 比如需要解绑数据源
    protected Object doSuspend(Object transaction) throws TransactionException {
        throw new TransactionSuspensionNotSupportedException("Transaction manager [" + this.getClass().getName() + "] does not support transaction suspension");
    }

    protected void doResume(@Nullable Object transaction, Object suspendedResources) throws TransactionException {
        throw new TransactionSuspensionNotSupportedException("Transaction manager [" + this.getClass().getName() + "] does not support transaction suspension");
    }

    protected boolean shouldCommitOnGlobalRollbackOnly() {
        return false;
    }

    protected void prepareForCommit(DefaultTransactionStatus status) {
    }

    protected abstract void doCommit(DefaultTransactionStatus var1) throws TransactionException;

    protected abstract void doRollback(DefaultTransactionStatus var1) throws TransactionException;

    protected void doSetRollbackOnly(DefaultTransactionStatus status) throws TransactionException {
        throw new IllegalTransactionStateException("Participating in existing transactions is not supported - when 'isExistingTransaction' returns true, appropriate 'doSetRollbackOnly' behavior must be provided");
    }

    protected void registerAfterCompletionWithExistingTransaction(Object transaction, List<TransactionSynchronization> synchronizations) throws TransactionException {
        this.logger.debug("Cannot register Spring after-completion synchronization with existing transaction - processing Spring after-completion callbacks immediately, with outcome status 'unknown'");
        this.invokeAfterCompletion(synchronizations, 2);
    }

    protected void doCleanupAfterCompletion(Object transaction) {
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        this.logger = LogFactory.getLog(this.getClass());
    }

	// 保存挂起的资源
    protected static final class SuspendedResourcesHolder {
        @Nullable
        private final Object suspendedResources;
        @Nullable
        private List<TransactionSynchronization> suspendedSynchronizations;
        @Nullable
        private String name;
        private boolean readOnly;
        @Nullable
        private Integer isolationLevel;
        private boolean wasActive;

        private SuspendedResourcesHolder(Object suspendedResources) {
            this.suspendedResources = suspendedResources;
        }

        private SuspendedResourcesHolder(@Nullable Object suspendedResources, List<TransactionSynchronization> suspendedSynchronizations, @Nullable String name, boolean readOnly, @Nullable Integer isolationLevel, boolean wasActive) {
            this.suspendedResources = suspendedResources;
            this.suspendedSynchronizations = suspendedSynchronizations;
            this.name = name;
            this.readOnly = readOnly;
            this.isolationLevel = isolationLevel;
            this.wasActive = wasActive;
        }
    }
}
