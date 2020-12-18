package org.springframework.jdbc.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import javax.sql.DataSource;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.lang.Nullable;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

// 数据源工具类
public abstract class DataSourceUtils {
    public static final int CONNECTION_SYNCHRONIZATION_ORDER = 1000;
    private static final Log logger = LogFactory.getLog(DataSourceUtils.class);

    public DataSourceUtils() {
    }

	// 根据数据源获取数据库连接
    public static Connection getConnection(DataSource dataSource) throws CannotGetJdbcConnectionException {
        try {
            return doGetConnection(dataSource);
        } catch (SQLException var2) {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection", var2);
        } catch (IllegalStateException var3) {
            throw new CannotGetJdbcConnectionException("Failed to obtain JDBC Connection: " + var3.getMessage());
        }
    }

	// 资源与管理器进行绑定
    public static Connection doGetConnection(DataSource dataSource) throws SQLException {
        Assert.notNull(dataSource, "No DataSource specified");
        ConnectionHolder conHolder = (ConnectionHolder)TransactionSynchronizationManager.getResource(dataSource);
        if (conHolder == null || !conHolder.hasConnection() && !conHolder.isSynchronizedWithTransaction()) {
            logger.debug("Fetching JDBC Connection from DataSource");
            Connection con = fetchConnection(dataSource);
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                try {
                    ConnectionHolder holderToUse = conHolder;
                    if (conHolder == null) {
                        holderToUse = new ConnectionHolder(con);
                    } else {
                        conHolder.setConnection(con);
                    }

                    holderToUse.requested();
                    TransactionSynchronizationManager.registerSynchronization(new DataSourceUtils.ConnectionSynchronization(holderToUse, dataSource));
                    holderToUse.setSynchronizedWithTransaction(true);
                    if (holderToUse != conHolder) {
                        TransactionSynchronizationManager.bindResource(dataSource, holderToUse);
                    }
                } catch (RuntimeException var4) {
                    releaseConnection(con, dataSource);
                    throw var4;
                }
            }

            return con;
        } else {
            conHolder.requested();
            if (!conHolder.hasConnection()) {
                logger.debug("Fetching resumed JDBC Connection from DataSource");
                conHolder.setConnection(fetchConnection(dataSource));
            }

            return conHolder.getConnection();
        }
    }

	// 从数据源中获取数据库连接
    private static Connection fetchConnection(DataSource dataSource) throws SQLException {
        Connection con = dataSource.getConnection();
        if (con == null) {
            throw new IllegalStateException("DataSource returned null from getConnection(): " + dataSource);
        } else {
            return con;
        }
    }

	// 事务前设置连接是否只读和隔离级别
    @Nullable
    public static Integer prepareConnectionForTransaction(Connection con, @Nullable TransactionDefinition definition) throws SQLException {
        Assert.notNull(con, "No Connection specified");
        boolean debugEnabled = logger.isDebugEnabled();
        if (definition != null && definition.isReadOnly()) {
            try {
                if (debugEnabled) {
                    logger.debug("Setting JDBC Connection [" + con + "] read-only");
                }

                con.setReadOnly(true);
            } catch (RuntimeException | SQLException var5) {
                for(Object exToCheck = var5; exToCheck != null; exToCheck = ((Throwable)exToCheck).getCause()) {
                    if (exToCheck.getClass().getSimpleName().contains("Timeout")) {
                        throw var5;
                    }
                }

                logger.debug("Could not set JDBC Connection read-only", var5);
            }
        }

        Integer previousIsolationLevel = null;
        if (definition != null && definition.getIsolationLevel() != -1) {
            if (debugEnabled) {
                logger.debug("Changing isolation level of JDBC Connection [" + con + "] to " + definition.getIsolationLevel());
            }

            int currentIsolation = con.getTransactionIsolation();
            if (currentIsolation != definition.getIsolationLevel()) {
                previousIsolationLevel = currentIsolation;
                con.setTransactionIsolation(definition.getIsolationLevel());
            }
        }

        return previousIsolationLevel;
    }

	// 事务后重置连接
    public static void resetConnectionAfterTransaction(Connection con, @Nullable Integer previousIsolationLevel, boolean resetReadOnly) {
        Assert.notNull(con, "No Connection specified");
        boolean debugEnabled = logger.isDebugEnabled();

        try {
            if (previousIsolationLevel != null) {
                if (debugEnabled) {
                    logger.debug("Resetting isolation level of JDBC Connection [" + con + "] to " + previousIsolationLevel);
                }

                con.setTransactionIsolation(previousIsolationLevel);
            }

            if (resetReadOnly) {
                if (debugEnabled) {
                    logger.debug("Resetting read-only flag of JDBC Connection [" + con + "]");
                }

                con.setReadOnly(false);
            }
        } catch (Throwable var5) {
            logger.debug("Could not reset JDBC Connection after transaction", var5);
        }

    }

    /** @deprecated */
    @Deprecated
    public static void resetConnectionAfterTransaction(Connection con, @Nullable Integer previousIsolationLevel) {
        Assert.notNull(con, "No Connection specified");

        try {
            if (previousIsolationLevel != null) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Resetting isolation level of JDBC Connection [" + con + "] to " + previousIsolationLevel);
                }

                con.setTransactionIsolation(previousIsolationLevel);
            }

            if (con.isReadOnly()) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Resetting read-only flag of JDBC Connection [" + con + "]");
                }

                con.setReadOnly(false);
            }
        } catch (Throwable var3) {
            logger.debug("Could not reset JDBC Connection after transaction", var3);
        }

    }

    public static boolean isConnectionTransactional(Connection con, @Nullable DataSource dataSource) {
        if (dataSource == null) {
            return false;
        } else {
            ConnectionHolder conHolder = (ConnectionHolder)TransactionSynchronizationManager.getResource(dataSource);
            return conHolder != null && connectionEquals(conHolder, con);
        }
    }

    public static void applyTransactionTimeout(Statement stmt, @Nullable DataSource dataSource) throws SQLException {
        applyTimeout(stmt, dataSource, -1);
    }

	// 设置Statement的查询超时时间
    public static void applyTimeout(Statement stmt, @Nullable DataSource dataSource, int timeout) throws SQLException {
        Assert.notNull(stmt, "No Statement specified");
        ConnectionHolder holder = null;
        if (dataSource != null) {
            holder = (ConnectionHolder)TransactionSynchronizationManager.getResource(dataSource);
        }

        if (holder != null && holder.hasTimeout()) {
            stmt.setQueryTimeout(holder.getTimeToLiveInSeconds());
        } else if (timeout >= 0) {
            stmt.setQueryTimeout(timeout);
        }

    }

    public static void releaseConnection(@Nullable Connection con, @Nullable DataSource dataSource) {
        try {
            doReleaseConnection(con, dataSource);
        } catch (SQLException var3) {
            logger.debug("Could not close JDBC Connection", var3);
        } catch (Throwable var4) {
            logger.debug("Unexpected exception on closing JDBC Connection", var4);
        }

    }

    public static void doReleaseConnection(@Nullable Connection con, @Nullable DataSource dataSource) throws SQLException {
        if (con != null) {
            if (dataSource != null) {
                ConnectionHolder conHolder = (ConnectionHolder)TransactionSynchronizationManager.getResource(dataSource);
                if (conHolder != null && connectionEquals(conHolder, con)) {
                    conHolder.released();
                    return;
                }
            }

            doCloseConnection(con, dataSource);
        }
    }

    public static void doCloseConnection(Connection con, @Nullable DataSource dataSource) throws SQLException {
        if (!(dataSource instanceof SmartDataSource) || ((SmartDataSource)dataSource).shouldClose(con)) {
            con.close();
        }

    }

    private static boolean connectionEquals(ConnectionHolder conHolder, Connection passedInCon) {
        if (!conHolder.hasConnection()) {
            return false;
        } else {
            Connection heldCon = conHolder.getConnection();
            return heldCon == passedInCon || heldCon.equals(passedInCon) || getTargetConnection(heldCon).equals(passedInCon);
        }
    }

    public static Connection getTargetConnection(Connection con) {
        Connection conToUse;
        for(conToUse = con; conToUse instanceof ConnectionProxy; conToUse = ((ConnectionProxy)conToUse).getTargetConnection()) {
        }

        return conToUse;
    }

    private static int getConnectionSynchronizationOrder(DataSource dataSource) {
        int order = 1000;

        for(DataSource currDs = dataSource; currDs instanceof DelegatingDataSource; currDs = ((DelegatingDataSource)currDs).getTargetDataSource()) {
            --order;
        }

        return order;
    }

    private static class ConnectionSynchronization extends TransactionSynchronizationAdapter {
        private final ConnectionHolder connectionHolder;
        private final DataSource dataSource;
        private int order;
        private boolean holderActive = true;

        public ConnectionSynchronization(ConnectionHolder connectionHolder, DataSource dataSource) {
            this.connectionHolder = connectionHolder;
            this.dataSource = dataSource;
            this.order = DataSourceUtils.getConnectionSynchronizationOrder(dataSource);
        }

        public int getOrder() {
            return this.order;
        }

        public void suspend() {
            if (this.holderActive) {
                TransactionSynchronizationManager.unbindResource(this.dataSource);
                if (this.connectionHolder.hasConnection() && !this.connectionHolder.isOpen()) {
                    DataSourceUtils.releaseConnection(this.connectionHolder.getConnection(), this.dataSource);
                    this.connectionHolder.setConnection((Connection)null);
                }
            }

        }

        public void resume() {
            if (this.holderActive) {
                TransactionSynchronizationManager.bindResource(this.dataSource, this.connectionHolder);
            }

        }

        public void beforeCompletion() {
            if (!this.connectionHolder.isOpen()) {
                TransactionSynchronizationManager.unbindResource(this.dataSource);
                this.holderActive = false;
                if (this.connectionHolder.hasConnection()) {
                    DataSourceUtils.releaseConnection(this.connectionHolder.getConnection(), this.dataSource);
                }
            }

        }

        public void afterCompletion(int status) {
            if (this.holderActive) {
                TransactionSynchronizationManager.unbindResourceIfPossible(this.dataSource);
                this.holderActive = false;
                if (this.connectionHolder.hasConnection()) {
                    DataSourceUtils.releaseConnection(this.connectionHolder.getConnection(), this.dataSource);
                    this.connectionHolder.setConnection((Connection)null);
                }
            }

            this.connectionHolder.reset();
        }
    }
}
