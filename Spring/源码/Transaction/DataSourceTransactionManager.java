package org.springframework.jdbc.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.lang.Nullable;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.ResourceTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;
import org.springframework.util.Assert;

// jdbc实现的事务管理器
public class DataSourceTransactionManager extends AbstractPlatformTransactionManager implements ResourceTransactionManager, InitializingBean {
    @Nullable
    private DataSource dataSource;					// 数据源
    private boolean enforceReadOnly;				// 是否强制只读

    public DataSourceTransactionManager() {
        this.enforceReadOnly = false;
        this.setNestedTransactionAllowed(true);
    }

    public DataSourceTransactionManager(DataSource dataSource) {
        this();
        this.setDataSource(dataSource);
        this.afterPropertiesSet();
    }

    public void setDataSource(@Nullable DataSource dataSource) {
        if (dataSource instanceof TransactionAwareDataSourceProxy) {
            this.dataSource = ((TransactionAwareDataSourceProxy)dataSource).getTargetDataSource();
        } else {
            this.dataSource = dataSource;
        }

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

    public void setEnforceReadOnly(boolean enforceReadOnly) {
        this.enforceReadOnly = enforceReadOnly;
    }

    public boolean isEnforceReadOnly() {
        return this.enforceReadOnly;
    }

    public void afterPropertiesSet() {
        if (this.getDataSource() == null) {
            throw new IllegalArgumentException("Property 'dataSource' is required");
        }
    }

    public Object getResourceFactory() {
        return this.obtainDataSource();
    }

	// 获取事务对象
    protected Object doGetTransaction() {
        DataSourceTransactionManager.DataSourceTransactionObject txObject = new DataSourceTransactionManager.DataSourceTransactionObject();
        txObject.setSavepointAllowed(this.isNestedTransactionAllowed());
        ConnectionHolder conHolder = (ConnectionHolder)TransactionSynchronizationManager.getResource(this.obtainDataSource());
        txObject.setConnectionHolder(conHolder, false);
        return txObject;
    }

    protected boolean isExistingTransaction(Object transaction) {
        DataSourceTransactionManager.DataSourceTransactionObject txObject = (DataSourceTransactionManager.DataSourceTransactionObject)transaction;
        return txObject.hasConnectionHolder() && txObject.getConnectionHolder().isTransactionActive();
    }

    protected void doBegin(Object transaction, TransactionDefinition definition) {
        DataSourceTransactionManager.DataSourceTransactionObject txObject = (DataSourceTransactionManager.DataSourceTransactionObject)transaction;
        Connection con = null;

        try {
            if (!txObject.hasConnectionHolder() || txObject.getConnectionHolder().isSynchronizedWithTransaction()) {
                Connection newCon = this.obtainDataSource().getConnection();
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("Acquired Connection [" + newCon + "] for JDBC transaction");
                }

                txObject.setConnectionHolder(new ConnectionHolder(newCon), true);
            }

            txObject.getConnectionHolder().setSynchronizedWithTransaction(true);
            con = txObject.getConnectionHolder().getConnection();
            Integer previousIsolationLevel = DataSourceUtils.prepareConnectionForTransaction(con, definition);
            txObject.setPreviousIsolationLevel(previousIsolationLevel);
            txObject.setReadOnly(definition.isReadOnly());
            if (con.getAutoCommit()) {
                txObject.setMustRestoreAutoCommit(true);
                if (this.logger.isDebugEnabled()) {
                    this.logger.debug("Switching JDBC Connection [" + con + "] to manual commit");
                }

                con.setAutoCommit(false);
            }

            this.prepareTransactionalConnection(con, definition);
            txObject.getConnectionHolder().setTransactionActive(true);
            int timeout = this.determineTimeout(definition);
            if (timeout != -1) {
                txObject.getConnectionHolder().setTimeoutInSeconds(timeout);
            }

            if (txObject.isNewConnectionHolder()) {
                TransactionSynchronizationManager.bindResource(this.obtainDataSource(), txObject.getConnectionHolder());
            }

        } catch (Throwable var7) {
            if (txObject.isNewConnectionHolder()) {
                DataSourceUtils.releaseConnection(con, this.obtainDataSource());
                txObject.setConnectionHolder((ConnectionHolder)null, false);
            }

            throw new CannotCreateTransactionException("Could not open JDBC Connection for transaction", var7);
        }
    }

    protected Object doSuspend(Object transaction) {
        DataSourceTransactionManager.DataSourceTransactionObject txObject = (DataSourceTransactionManager.DataSourceTransactionObject)transaction;
        txObject.setConnectionHolder((ConnectionHolder)null);
        return TransactionSynchronizationManager.unbindResource(this.obtainDataSource());
    }

    protected void doResume(@Nullable Object transaction, Object suspendedResources) {
        TransactionSynchronizationManager.bindResource(this.obtainDataSource(), suspendedResources);
    }

    protected void doCommit(DefaultTransactionStatus status) {
        DataSourceTransactionManager.DataSourceTransactionObject txObject = (DataSourceTransactionManager.DataSourceTransactionObject)status.getTransaction();
        Connection con = txObject.getConnectionHolder().getConnection();
        if (status.isDebug()) {
            this.logger.debug("Committing JDBC transaction on Connection [" + con + "]");
        }

        try {
            con.commit();
        } catch (SQLException var5) {
            throw new TransactionSystemException("Could not commit JDBC transaction", var5);
        }
    }

    protected void doRollback(DefaultTransactionStatus status) {
        DataSourceTransactionManager.DataSourceTransactionObject txObject = (DataSourceTransactionManager.DataSourceTransactionObject)status.getTransaction();
        Connection con = txObject.getConnectionHolder().getConnection();
        if (status.isDebug()) {
            this.logger.debug("Rolling back JDBC transaction on Connection [" + con + "]");
        }

        try {
            con.rollback();
        } catch (SQLException var5) {
            throw new TransactionSystemException("Could not roll back JDBC transaction", var5);
        }
    }

    protected void doSetRollbackOnly(DefaultTransactionStatus status) {
        DataSourceTransactionManager.DataSourceTransactionObject txObject = (DataSourceTransactionManager.DataSourceTransactionObject)status.getTransaction();
        if (status.isDebug()) {
            this.logger.debug("Setting JDBC transaction [" + txObject.getConnectionHolder().getConnection() + "] rollback-only");
        }

        txObject.setRollbackOnly();
    }

    protected void doCleanupAfterCompletion(Object transaction) {
        DataSourceTransactionManager.DataSourceTransactionObject txObject = (DataSourceTransactionManager.DataSourceTransactionObject)transaction;
        if (txObject.isNewConnectionHolder()) {
            TransactionSynchronizationManager.unbindResource(this.obtainDataSource());
        }

        Connection con = txObject.getConnectionHolder().getConnection();

        try {
            if (txObject.isMustRestoreAutoCommit()) {
                con.setAutoCommit(true);
            }

            DataSourceUtils.resetConnectionAfterTransaction(con, txObject.getPreviousIsolationLevel(), txObject.isReadOnly());
        } catch (Throwable var5) {
            this.logger.debug("Could not reset JDBC Connection after transaction", var5);
        }

        if (txObject.isNewConnectionHolder()) {
            if (this.logger.isDebugEnabled()) {
                this.logger.debug("Releasing JDBC Connection [" + con + "] after transaction");
            }

            DataSourceUtils.releaseConnection(con, this.dataSource);
        }

        txObject.getConnectionHolder().clear();
    }

    protected void prepareTransactionalConnection(Connection con, TransactionDefinition definition) throws SQLException {
        if (this.isEnforceReadOnly() && definition.isReadOnly()) {
            Statement stmt = con.createStatement();
            Throwable var4 = null;

            try {
                stmt.executeUpdate("SET TRANSACTION READ ONLY");
            } catch (Throwable var13) {
                var4 = var13;
                throw var13;
            } finally {
                if (stmt != null) {
                    if (var4 != null) {
                        try {
                            stmt.close();
                        } catch (Throwable var12) {
                            var4.addSuppressed(var12);
                        }
                    } else {
                        stmt.close();
                    }
                }

            }
        }

    }

    private static class DataSourceTransactionObject extends JdbcTransactionObjectSupport {
        private boolean newConnectionHolder;
        private boolean mustRestoreAutoCommit;

        private DataSourceTransactionObject() {
        }

        public void setConnectionHolder(@Nullable ConnectionHolder connectionHolder, boolean newConnectionHolder) {
            super.setConnectionHolder(connectionHolder);
            this.newConnectionHolder = newConnectionHolder;
        }

        public boolean isNewConnectionHolder() {
            return this.newConnectionHolder;
        }

        public void setMustRestoreAutoCommit(boolean mustRestoreAutoCommit) {
            this.mustRestoreAutoCommit = mustRestoreAutoCommit;
        }

        public boolean isMustRestoreAutoCommit() {
            return this.mustRestoreAutoCommit;
        }

        public void setRollbackOnly() {
            this.getConnectionHolder().setRollbackOnly();
        }

        public boolean isRollbackOnly() {
            return this.getConnectionHolder().isRollbackOnly();
        }

        public void flush() {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationUtils.triggerFlush();
            }

        }
    }
}
