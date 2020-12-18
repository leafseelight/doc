package org.springframework.jdbc.datasource;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;
import org.springframework.lang.Nullable;
import org.springframework.transaction.support.ResourceHolderSupport;
import org.springframework.util.Assert;

// 连接持有，对Connection的一层封装
public class ConnectionHolder extends ResourceHolderSupport {
    public static final String SAVEPOINT_NAME_PREFIX = "SAVEPOINT_";	// 保存点前缀
    @Nullable
    private ConnectionHandle connectionHandle;		// 封装连接获取和释放操作
    @Nullable
    private Connection currentConnection;			// 封装了当前连接
    private boolean transactionActive;				// 是否启用事务
    @Nullable
    private Boolean savepointsSupported;			// 是否支持保存点
    private int savepointCounter;					// 保存点计数 结合前缀SAVEPOINT_来生成保存点名称

    public ConnectionHolder(ConnectionHandle connectionHandle) {
        this.transactionActive = false;
        this.savepointCounter = 0;
        Assert.notNull(connectionHandle, "ConnectionHandle must not be null");
        this.connectionHandle = connectionHandle;
    }

    public ConnectionHolder(Connection connection) {
        this.transactionActive = false;
        this.savepointCounter = 0;
        this.connectionHandle = new SimpleConnectionHandle(connection);
    }

    public ConnectionHolder(Connection connection, boolean transactionActive) {
        this(connection);
        this.transactionActive = transactionActive;
    }

    @Nullable
    public ConnectionHandle getConnectionHandle() {
        return this.connectionHandle;
    }

    protected boolean hasConnection() {
        return this.connectionHandle != null;
    }

    protected void setTransactionActive(boolean transactionActive) {
        this.transactionActive = transactionActive;
    }

    protected boolean isTransactionActive() {
        return this.transactionActive;
    }

    protected void setConnection(@Nullable Connection connection) {
        if (this.currentConnection != null) {
            if (this.connectionHandle != null) {
                this.connectionHandle.releaseConnection(this.currentConnection);
            }

            this.currentConnection = null;
        }

        if (connection != null) {
            this.connectionHandle = new SimpleConnectionHandle(connection);
        } else {
            this.connectionHandle = null;
        }

    }

    public Connection getConnection() {
        Assert.notNull(this.connectionHandle, "Active Connection is required");
        if (this.currentConnection == null) {
            this.currentConnection = this.connectionHandle.getConnection();
        }

        return this.currentConnection;
    }

    public boolean supportsSavepoints() throws SQLException {
        if (this.savepointsSupported == null) {
            this.savepointsSupported = this.getConnection().getMetaData().supportsSavepoints();
        }

        return this.savepointsSupported;
    }

	// 创建保存点
    public Savepoint createSavepoint() throws SQLException {
        ++this.savepointCounter;
        return this.getConnection().setSavepoint("SAVEPOINT_" + this.savepointCounter);
    }

    public void released() {
        super.released();
        if (!this.isOpen() && this.currentConnection != null) {
            if (this.connectionHandle != null) {
                this.connectionHandle.releaseConnection(this.currentConnection);
            }

            this.currentConnection = null;
        }

    }

    public void clear() {
        super.clear();
        this.transactionActive = false;
        this.savepointsSupported = null;
        this.savepointCounter = 0;
    }
}
