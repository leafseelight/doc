package org.springframework.jdbc.core;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.BatchUpdateException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLWarning;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.SQLWarningException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.datasource.ConnectionProxy;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.support.JdbcAccessor;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.jdbc.support.rowset.SqlRowSet;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.StringUtils;

public class JdbcTemplate extends JdbcAccessor implements JdbcOperations {
    private static final String RETURN_RESULT_SET_PREFIX = "#result-set-";
    private static final String RETURN_UPDATE_COUNT_PREFIX = "#update-count-";
    private boolean ignoreWarnings = true;					// 默认忽略警告
    private int fetchSize = -1;								// 
    private int maxRows = -1;								// 最大行
    private int queryTimeout = -1;
    private boolean skipResultsProcessing = false;
    private boolean skipUndeclaredResults = false;
    private boolean resultsMapCaseInsensitive = false;		// 结果集默认大小写不敏感

    public JdbcTemplate() {
    }

    public JdbcTemplate(DataSource dataSource) {
        this.setDataSource(dataSource);
        this.afterPropertiesSet();
    }

	// SQL异常处理器是否懒加载
    public JdbcTemplate(DataSource dataSource, boolean lazyInit) {
        this.setDataSource(dataSource);
        this.setLazyInit(lazyInit);
        this.afterPropertiesSet();
    }

    public void setIgnoreWarnings(boolean ignoreWarnings) {
        this.ignoreWarnings = ignoreWarnings;
    }

    public boolean isIgnoreWarnings() {
        return this.ignoreWarnings;
    }

    public void setFetchSize(int fetchSize) {
        this.fetchSize = fetchSize;
    }

    public int getFetchSize() {
        return this.fetchSize;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }

    public int getMaxRows() {
        return this.maxRows;
    }

    public void setQueryTimeout(int queryTimeout) {
        this.queryTimeout = queryTimeout;
    }

    public int getQueryTimeout() {
        return this.queryTimeout;
    }

    public void setSkipResultsProcessing(boolean skipResultsProcessing) {
        this.skipResultsProcessing = skipResultsProcessing;
    }

    public boolean isSkipResultsProcessing() {
        return this.skipResultsProcessing;
    }

    public void setSkipUndeclaredResults(boolean skipUndeclaredResults) {
        this.skipUndeclaredResults = skipUndeclaredResults;
    }

    public boolean isSkipUndeclaredResults() {
        return this.skipUndeclaredResults;
    }

    public void setResultsMapCaseInsensitive(boolean resultsMapCaseInsensitive) {
        this.resultsMapCaseInsensitive = resultsMapCaseInsensitive;
    }

    public boolean isResultsMapCaseInsensitive() {
        return this.resultsMapCaseInsensitive;
    }

	// 执行连接操作
    @Nullable
    public <T> T execute(ConnectionCallback<T> action) throws DataAccessException {
        Assert.notNull(action, "Callback object must not be null");
		// 获取数据库连接
        Connection con = DataSourceUtils.getConnection(this.obtainDataSource());

        Object var10;
        try {
			// 创建连接代理ConnectionProxy
            Connection conToUse = this.createConnectionProxy(con);
			// 回调
            var10 = action.doInConnection(conToUse);
        } catch (SQLException var8) {
            String sql = getSql(action);
            DataSourceUtils.releaseConnection(con, this.getDataSource());
            con = null;
            throw this.translateException("ConnectionCallback", sql, var8);
        } finally {
            DataSourceUtils.releaseConnection(con, this.getDataSource());
        }

        return var10;
    }

    protected Connection createConnectionProxy(Connection con) {
        return (Connection)Proxy.newProxyInstance(ConnectionProxy.class.getClassLoader(), new Class[]{ConnectionProxy.class}, new JdbcTemplate.CloseSuppressingInvocationHandler(con));
    }

	// 执行Statement
    @Nullable
    public <T> T execute(StatementCallback<T> action) throws DataAccessException {
        Assert.notNull(action, "Callback object must not be null");
        Connection con = DataSourceUtils.getConnection(this.obtainDataSource());
        Statement stmt = null;

        Object var11;
        try {
			// 通过连接创建Statement
            stmt = con.createStatement();
			// 设置Statement的超时等
            this.applyStatementSettings(stmt);
            T result = action.doInStatement(stmt);
			// 异常处理 默认打日志而不是抛出SQLWarningException
            this.handleWarnings(stmt);
            var11 = result;
        } catch (SQLException var9) {
            String sql = getSql(action);
            JdbcUtils.closeStatement(stmt);
            stmt = null;
			// 执行抛出异常后会释放连接
            DataSourceUtils.releaseConnection(con, this.getDataSource());
            con = null;
            throw this.translateException("StatementCallback", sql, var9);
        } finally {
            JdbcUtils.closeStatement(stmt);
            DataSourceUtils.releaseConnection(con, this.getDataSource());
        }

        return var11;
    }

	// 执行sql
    public void execute(final String sql) throws DataAccessException {
        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Executing SQL statement [" + sql + "]");
        }

        class ExecuteStatementCallback implements StatementCallback<Object>, SqlProvider {
            ExecuteStatementCallback() {
            }

            @Nullable
            public Object doInStatement(Statement stmt) throws SQLException {
                stmt.execute(sql);
                return null;
            }

            public String getSql() {
                return sql;
            }
        }

        this.execute((StatementCallback)(new ExecuteStatementCallback()));
    }

	// 查询  传入结果集抽取器
    @Nullable
    public <T> T query(final String sql, final ResultSetExtractor<T> rse) throws DataAccessException {
        Assert.notNull(sql, "SQL must not be null");
        Assert.notNull(rse, "ResultSetExtractor must not be null");
        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Executing SQL query [" + sql + "]");
        }

        class QueryStatementCallback implements StatementCallback<T>, SqlProvider {
            QueryStatementCallback() {
            }

            @Nullable
            public T doInStatement(Statement stmt) throws SQLException {
                ResultSet rs = null;

                Object var3;
                try {
					// 查询
                    rs = stmt.executeQuery(sql);
					// 从结果集中抽取数据
                    var3 = rse.extractData(rs);
                } finally {
                    JdbcUtils.closeResultSet(rs);
                }

                return var3;
            }

            public String getSql() {
                return sql;
            }
        }

        return this.execute((StatementCallback)(new QueryStatementCallback()));
    }

	// 按行处理结果集 RowCallbackHandler会被封装成ResultSetExtractor
    public void query(String sql, RowCallbackHandler rch) throws DataAccessException {
        this.query((String)sql, (ResultSetExtractor)(new JdbcTemplate.RowCallbackHandlerResultSetExtractor(rch)));
    }

	// 行映射器 rowMapper会被封装成ResultSetExtractor
	// RowMapperResultSetExtractor是结果集抽取器的一个实现 即一行一行进行mapRow，并返回list结果集合
    public <T> List<T> query(String sql, RowMapper<T> rowMapper) throws DataAccessException {
        return (List)result(this.query((String)sql, (ResultSetExtractor)(new RowMapperResultSetExtractor(rowMapper))));
    }

	// 查询并把结果封装成map
    public Map<String, Object> queryForMap(String sql) throws DataAccessException {
        return (Map)result(this.queryForObject(sql, this.getColumnMapRowMapper()));
    }

	// 查询并把结果封装成Object
    @Nullable
    public <T> T queryForObject(String sql, RowMapper<T> rowMapper) throws DataAccessException {
        List<T> results = this.query(sql, rowMapper);
        return DataAccessUtils.nullableSingleResult(results);  // 【数据为空也会报错？】
    }

    @Nullable
    public <T> T queryForObject(String sql, Class<T> requiredType) throws DataAccessException {
        return this.queryForObject(sql, this.getSingleColumnRowMapper(requiredType)); // 【只能映射一列？是的，它不是处理javabean的】
    }

    public <T> List<T> queryForList(String sql, Class<T> elementType) throws DataAccessException {
        return this.query(sql, this.getSingleColumnRowMapper(elementType)); 
    }

	// 无参queryForList方法返回List<Map>
    public List<Map<String, Object>> queryForList(String sql) throws DataAccessException {
        return this.query(sql, this.getColumnMapRowMapper());
    }

    public SqlRowSet queryForRowSet(String sql) throws DataAccessException {
        return (SqlRowSet)result(this.query((String)sql, (ResultSetExtractor)(new SqlRowSetResultSetExtractor())));
    }

    public int update(final String sql) throws DataAccessException {
        Assert.notNull(sql, "SQL must not be null");
        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Executing SQL update [" + sql + "]");
        }

        class UpdateStatementCallback implements StatementCallback<Integer>, SqlProvider {
            UpdateStatementCallback() {
            }

            public Integer doInStatement(Statement stmt) throws SQLException {
                int rows = stmt.executeUpdate(sql);
                if (JdbcTemplate.this.logger.isTraceEnabled()) {
                    JdbcTemplate.this.logger.trace("SQL update affected " + rows + " rows");
                }

                return rows;
            }

            public String getSql() {
                return sql;
            }
        }

        return updateCount((Integer)this.execute((StatementCallback)(new UpdateStatementCallback())));
    }

	// 批量更新
    public int[] batchUpdate(final String... sql) throws DataAccessException {
        Assert.notEmpty(sql, "SQL array must not be empty");
        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Executing SQL batch update of " + sql.length + " statements");
        }

        class BatchUpdateStatementCallback implements StatementCallback<int[]>, SqlProvider {
            @Nullable
            private String currSql;

            BatchUpdateStatementCallback() {
            }

            public int[] doInStatement(Statement stmt) throws SQLException, DataAccessException {
                int[] rowsAffected = new int[sql.length];
                if (JdbcUtils.supportsBatchUpdates(stmt.getConnection())) {
                    String[] var3 = sql;
                    int var4 = var3.length;

                    int ix;
                    for(ix = 0; ix < var4; ++ix) {
                        String sqlStmt = var3[ix];
                        this.currSql = this.appendSql(this.currSql, sqlStmt);  // 多条sql拼接 ;分隔
                        stmt.addBatch(sqlStmt);
                    }

                    try {
                        rowsAffected = stmt.executeBatch();
                    } catch (BatchUpdateException var7) {
                        BatchUpdateException ex = var7;
                        String batchExceptionSql = null;

                        for(ix = 0; ix < ex.getUpdateCounts().length; ++ix) {
                            if (ex.getUpdateCounts()[ix] == -3) {
                                batchExceptionSql = this.appendSql(batchExceptionSql, sql[ix]);
                            }
                        }

                        if (StringUtils.hasLength(batchExceptionSql)) {
                            this.currSql = batchExceptionSql;
                        }

                        throw ex;
                    }
                } else {
					// 如果不支持批量更新 则循环执行
                    for(int i = 0; i < sql.length; ++i) {
                        this.currSql = sql[i];
                        if (stmt.execute(sql[i])) {
                            throw new InvalidDataAccessApiUsageException("Invalid batch SQL statement: " + sql[i]);
                        }

                        rowsAffected[i] = stmt.getUpdateCount();
                    }
                }

                return rowsAffected;
            }

            private String appendSql(@Nullable String sqlx, String statement) {
                return StringUtils.hasLength(sqlx) ? sqlx + "; " + statement : statement;
            }

            @Nullable
            public String getSql() {
                return this.currSql;
            }
        }

        int[] result = (int[])this.execute((StatementCallback)(new BatchUpdateStatementCallback()));
        Assert.state(result != null, "No update counts");
        return result;
    }

    @Nullable
    public <T> T execute(PreparedStatementCreator psc, PreparedStatementCallback<T> action) throws DataAccessException {
        Assert.notNull(psc, "PreparedStatementCreator must not be null");
        Assert.notNull(action, "Callback object must not be null");
        if (this.logger.isDebugEnabled()) {
            String sql = getSql(psc);
            this.logger.debug("Executing prepared SQL statement" + (sql != null ? " [" + sql + "]" : ""));
        }

        Connection con = DataSourceUtils.getConnection(this.obtainDataSource());
        PreparedStatement ps = null;

        Object var13;
        try {
			// 创建预编译语句
            ps = psc.createPreparedStatement(con);
            this.applyStatementSettings(ps);
            T result = action.doInPreparedStatement(ps);
            this.handleWarnings((Statement)ps);
            var13 = result;
        } catch (SQLException var10) {
            if (psc instanceof ParameterDisposer) {
                ((ParameterDisposer)psc).cleanupParameters();
            }

            String sql = getSql(psc);
            psc = null;
            JdbcUtils.closeStatement(ps);
            ps = null;
            DataSourceUtils.releaseConnection(con, this.getDataSource());
            con = null;
            throw this.translateException("PreparedStatementCallback", sql, var10);
        } finally {
            if (psc instanceof ParameterDisposer) {
                ((ParameterDisposer)psc).cleanupParameters();
            }

            JdbcUtils.closeStatement(ps);
            DataSourceUtils.releaseConnection(con, this.getDataSource());
        }

        return var13;
    }

    @Nullable
    public <T> T execute(String sql, PreparedStatementCallback<T> action) throws DataAccessException {
        return this.execute((PreparedStatementCreator)(new JdbcTemplate.SimplePreparedStatementCreator(sql)), (PreparedStatementCallback)action);
    }

    @Nullable
    public <T> T query(PreparedStatementCreator psc, @Nullable final PreparedStatementSetter pss, final ResultSetExtractor<T> rse) throws DataAccessException {
        Assert.notNull(rse, "ResultSetExtractor must not be null");
        this.logger.debug("Executing prepared SQL query");
        return this.execute(psc, new PreparedStatementCallback<T>() {
            @Nullable
            public T doInPreparedStatement(PreparedStatement ps) throws SQLException {
                ResultSet rs = null;

                Object var3;
                try {
                    if (pss != null) {
                        pss.setValues(ps);
                    }

                    rs = ps.executeQuery();
                    var3 = rse.extractData(rs);
                } finally {
                    JdbcUtils.closeResultSet(rs);
                    if (pss instanceof ParameterDisposer) {
                        ((ParameterDisposer)pss).cleanupParameters();
                    }

                }

                return var3;
            }
        });
    }

    @Nullable
    public <T> T query(PreparedStatementCreator psc, ResultSetExtractor<T> rse) throws DataAccessException {
        return this.query((PreparedStatementCreator)psc, (PreparedStatementSetter)null, (ResultSetExtractor)rse);
    }

    @Nullable
    public <T> T query(String sql, @Nullable PreparedStatementSetter pss, ResultSetExtractor<T> rse) throws DataAccessException {
        return this.query((PreparedStatementCreator)(new JdbcTemplate.SimplePreparedStatementCreator(sql)), (PreparedStatementSetter)pss, (ResultSetExtractor)rse);
    }

    @Nullable
    public <T> T query(String sql, Object[] args, int[] argTypes, ResultSetExtractor<T> rse) throws DataAccessException {
        return this.query(sql, this.newArgTypePreparedStatementSetter(args, argTypes), rse);
    }

    @Nullable
    public <T> T query(String sql, @Nullable Object[] args, ResultSetExtractor<T> rse) throws DataAccessException {
        return this.query(sql, this.newArgPreparedStatementSetter(args), rse);
    }

    @Nullable
    public <T> T query(String sql, ResultSetExtractor<T> rse, @Nullable Object... args) throws DataAccessException {
        return this.query(sql, this.newArgPreparedStatementSetter(args), rse);
    }

    public void query(PreparedStatementCreator psc, RowCallbackHandler rch) throws DataAccessException {
        this.query((PreparedStatementCreator)psc, (ResultSetExtractor)(new JdbcTemplate.RowCallbackHandlerResultSetExtractor(rch)));
    }

    public void query(String sql, @Nullable PreparedStatementSetter pss, RowCallbackHandler rch) throws DataAccessException {
        this.query((String)sql, (PreparedStatementSetter)pss, (ResultSetExtractor)(new JdbcTemplate.RowCallbackHandlerResultSetExtractor(rch)));
    }

    public void query(String sql, Object[] args, int[] argTypes, RowCallbackHandler rch) throws DataAccessException {
        this.query(sql, this.newArgTypePreparedStatementSetter(args, argTypes), rch);
    }

    public void query(String sql, @Nullable Object[] args, RowCallbackHandler rch) throws DataAccessException {
        this.query(sql, this.newArgPreparedStatementSetter(args), rch);
    }

    public void query(String sql, RowCallbackHandler rch, @Nullable Object... args) throws DataAccessException {
        this.query(sql, this.newArgPreparedStatementSetter(args), rch);
    }

    public <T> List<T> query(PreparedStatementCreator psc, RowMapper<T> rowMapper) throws DataAccessException {
        return (List)result(this.query((PreparedStatementCreator)psc, (ResultSetExtractor)(new RowMapperResultSetExtractor(rowMapper))));
    }

    public <T> List<T> query(String sql, @Nullable PreparedStatementSetter pss, RowMapper<T> rowMapper) throws DataAccessException {
        return (List)result(this.query((String)sql, (PreparedStatementSetter)pss, (ResultSetExtractor)(new RowMapperResultSetExtractor(rowMapper))));
    }

    public <T> List<T> query(String sql, Object[] args, int[] argTypes, RowMapper<T> rowMapper) throws DataAccessException {
        return (List)result(this.query(sql, args, argTypes, (ResultSetExtractor)(new RowMapperResultSetExtractor(rowMapper))));
    }

	// 查询 带参数
	// 参数会通过ArgumentPreparedStatementSetter 设置到PreparedStatement
	// 【不会产生sql注入，前提是sql字符串本身是安全的】
    public <T> List<T> query(String sql, @Nullable Object[] args, RowMapper<T> rowMapper) throws DataAccessException {
        return (List)result(this.query((String)sql, (Object[])args, (ResultSetExtractor)(new RowMapperResultSetExtractor(rowMapper))));
    }

	// 查询 带参数 动态参数
    public <T> List<T> query(String sql, RowMapper<T> rowMapper, @Nullable Object... args) throws DataAccessException {
        return (List)result(this.query((String)sql, (Object[])args, (ResultSetExtractor)(new RowMapperResultSetExtractor(rowMapper))));
    }

    @Nullable
    public <T> T queryForObject(String sql, Object[] args, int[] argTypes, RowMapper<T> rowMapper) throws DataAccessException {
        List<T> results = (List)this.query(sql, args, argTypes, (ResultSetExtractor)(new RowMapperResultSetExtractor(rowMapper, 1)));
        return DataAccessUtils.nullableSingleResult(results);
    }

    @Nullable
    public <T> T queryForObject(String sql, @Nullable Object[] args, RowMapper<T> rowMapper) throws DataAccessException {
        List<T> results = (List)this.query((String)sql, (Object[])args, (ResultSetExtractor)(new RowMapperResultSetExtractor(rowMapper, 1)));
        return DataAccessUtils.nullableSingleResult(results);
    }

    @Nullable
    public <T> T queryForObject(String sql, RowMapper<T> rowMapper, @Nullable Object... args) throws DataAccessException {
        List<T> results = (List)this.query((String)sql, (Object[])args, (ResultSetExtractor)(new RowMapperResultSetExtractor(rowMapper, 1)));
        return DataAccessUtils.nullableSingleResult(results);
    }

    @Nullable
    public <T> T queryForObject(String sql, Object[] args, int[] argTypes, Class<T> requiredType) throws DataAccessException {
        return this.queryForObject(sql, args, argTypes, this.getSingleColumnRowMapper(requiredType));
    }

    public <T> T queryForObject(String sql, @Nullable Object[] args, Class<T> requiredType) throws DataAccessException {
        return this.queryForObject(sql, args, this.getSingleColumnRowMapper(requiredType));
    }

    public <T> T queryForObject(String sql, Class<T> requiredType, @Nullable Object... args) throws DataAccessException {
        return this.queryForObject(sql, args, this.getSingleColumnRowMapper(requiredType));
    }

    public Map<String, Object> queryForMap(String sql, Object[] args, int[] argTypes) throws DataAccessException {
        return (Map)result(this.queryForObject(sql, args, argTypes, this.getColumnMapRowMapper()));
    }

    public Map<String, Object> queryForMap(String sql, @Nullable Object... args) throws DataAccessException {
        return (Map)result(this.queryForObject(sql, args, this.getColumnMapRowMapper()));
    }

    public <T> List<T> queryForList(String sql, Object[] args, int[] argTypes, Class<T> elementType) throws DataAccessException {
        return this.query(sql, args, argTypes, this.getSingleColumnRowMapper(elementType));
    }

    public <T> List<T> queryForList(String sql, @Nullable Object[] args, Class<T> elementType) throws DataAccessException {
        return this.query(sql, args, this.getSingleColumnRowMapper(elementType));
    }

    public <T> List<T> queryForList(String sql, Class<T> elementType, @Nullable Object... args) throws DataAccessException {
        return this.query(sql, args, this.getSingleColumnRowMapper(elementType));
    }

    public List<Map<String, Object>> queryForList(String sql, Object[] args, int[] argTypes) throws DataAccessException {
        return this.query(sql, args, argTypes, this.getColumnMapRowMapper());
    }

    public List<Map<String, Object>> queryForList(String sql, @Nullable Object... args) throws DataAccessException {
        return this.query(sql, args, this.getColumnMapRowMapper());
    }

    public SqlRowSet queryForRowSet(String sql, Object[] args, int[] argTypes) throws DataAccessException {
        return (SqlRowSet)result(this.query(sql, args, argTypes, (ResultSetExtractor)(new SqlRowSetResultSetExtractor())));
    }

    public SqlRowSet queryForRowSet(String sql, @Nullable Object... args) throws DataAccessException {
        return (SqlRowSet)result(this.query((String)sql, (Object[])args, (ResultSetExtractor)(new SqlRowSetResultSetExtractor())));
    }

    protected int update(PreparedStatementCreator psc, @Nullable PreparedStatementSetter pss) throws DataAccessException {
        this.logger.debug("Executing prepared SQL update");
        return updateCount((Integer)this.execute(psc, (ps) -> {
            Integer var4;
            try {
                if (pss != null) {
                    pss.setValues(ps);
                }

                int rows = ps.executeUpdate();
                if (this.logger.isTraceEnabled()) {
                    this.logger.trace("SQL update affected " + rows + " rows");
                }

                var4 = rows;
            } finally {
                if (pss instanceof ParameterDisposer) {
                    ((ParameterDisposer)pss).cleanupParameters();
                }

            }

            return var4;
        }));
    }

    public int update(PreparedStatementCreator psc) throws DataAccessException {
        return this.update(psc, (PreparedStatementSetter)null);
    }

    public int update(PreparedStatementCreator psc, KeyHolder generatedKeyHolder) throws DataAccessException {
        Assert.notNull(generatedKeyHolder, "KeyHolder must not be null");
        this.logger.debug("Executing SQL update and returning generated keys");
        return updateCount((Integer)this.execute(psc, (ps) -> {
            int rows = ps.executeUpdate();
            List<Map<String, Object>> generatedKeys = generatedKeyHolder.getKeyList();
            generatedKeys.clear();
            ResultSet keys = ps.getGeneratedKeys();
            if (keys != null) {
                try {
                    RowMapperResultSetExtractor<Map<String, Object>> rse = new RowMapperResultSetExtractor(this.getColumnMapRowMapper(), 1);
                    generatedKeys.addAll((Collection)result(rse.extractData(keys)));
                } finally {
                    JdbcUtils.closeResultSet(keys);
                }
            }

            if (this.logger.isTraceEnabled()) {
                this.logger.trace("SQL update affected " + rows + " rows and returned " + generatedKeys.size() + " keys");
            }

            return rows;
        }));
    }

    public int update(String sql, @Nullable PreparedStatementSetter pss) throws DataAccessException {
        return this.update((PreparedStatementCreator)(new JdbcTemplate.SimplePreparedStatementCreator(sql)), (PreparedStatementSetter)pss);
    }

    public int update(String sql, Object[] args, int[] argTypes) throws DataAccessException {
        return this.update(sql, this.newArgTypePreparedStatementSetter(args, argTypes));
    }

    public int update(String sql, @Nullable Object... args) throws DataAccessException {
        return this.update(sql, this.newArgPreparedStatementSetter(args));
    }

    public int[] batchUpdate(String sql, BatchPreparedStatementSetter pss) throws DataAccessException {
        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Executing SQL batch update [" + sql + "]");
        }

        int[] result = (int[])this.execute(sql, (ps) -> {
            try {
                int batchSize = pss.getBatchSize();
                InterruptibleBatchPreparedStatementSetter ipss = pss instanceof InterruptibleBatchPreparedStatementSetter ? (InterruptibleBatchPreparedStatementSetter)pss : null;
                if (!JdbcUtils.supportsBatchUpdates(ps.getConnection())) {
                    List<Integer> rowsAffected = new ArrayList();
                    int ix = 0;

                    while(true) {
                        if (ix < batchSize) {
                            pss.setValues(ps, ix);
                            if (ipss == null || !ipss.isBatchExhausted(ix)) {
                                rowsAffected.add(ps.executeUpdate());
                                ++ix;
                                continue;
                            }
                        }

                        int[] rowsAffectedArray = new int[rowsAffected.size()];

                        for(int ixx = 0; ixx < rowsAffectedArray.length; ++ixx) {
                            rowsAffectedArray[ixx] = (Integer)rowsAffected.get(ixx);
                        }

                        int[] var13 = rowsAffectedArray;
                        return var13;
                    }
                } else {
                    int i = 0;

                    while(true) {
                        if (i < batchSize) {
                            pss.setValues(ps, i);
                            if (ipss == null || !ipss.isBatchExhausted(i)) {
                                ps.addBatch();
                                ++i;
                                continue;
                            }
                        }

                        int[] var10 = ps.executeBatch();
                        return var10;
                    }
                }
            } finally {
                if (pss instanceof ParameterDisposer) {
                    ((ParameterDisposer)pss).cleanupParameters();
                }

            }
        });
        Assert.state(result != null, "No result array");
        return result;
    }

    public int[] batchUpdate(String sql, List<Object[]> batchArgs) throws DataAccessException {
        return this.batchUpdate(sql, batchArgs, new int[0]);
    }

    public int[] batchUpdate(String sql, final List<Object[]> batchArgs, final int[] argTypes) throws DataAccessException {
        return batchArgs.isEmpty() ? new int[0] : this.batchUpdate(sql, new BatchPreparedStatementSetter() {
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                Object[] values = (Object[])batchArgs.get(i);
                int colIndex = 0;
                Object[] var5 = values;
                int var6 = values.length;

                for(int var7 = 0; var7 < var6; ++var7) {
                    Object value = var5[var7];
                    ++colIndex;
                    if (value instanceof SqlParameterValue) {
                        SqlParameterValue paramValue = (SqlParameterValue)value;
                        StatementCreatorUtils.setParameterValue(ps, colIndex, paramValue, paramValue.getValue());
                    } else {
                        int colType;
                        if (argTypes.length < colIndex) {
                            colType = -2147483648;
                        } else {
                            colType = argTypes[colIndex - 1];
                        }

                        StatementCreatorUtils.setParameterValue(ps, colIndex, colType, value);
                    }
                }

            }

            public int getBatchSize() {
                return batchArgs.size();
            }
        });
    }

    public <T> int[][] batchUpdate(String sql, Collection<T> batchArgs, int batchSize, ParameterizedPreparedStatementSetter<T> pss) throws DataAccessException {
        if (this.logger.isDebugEnabled()) {
            this.logger.debug("Executing SQL batch update [" + sql + "] with a batch size of " + batchSize);
        }

        int[][] result = (int[][])this.execute(sql, (ps) -> {
            ArrayList rowsAffected = new ArrayList();

            try {
                boolean batchSupported = JdbcUtils.supportsBatchUpdates(ps.getConnection());
                int n = 0;
                Iterator var8 = batchArgs.iterator();

                while(var8.hasNext()) {
                    T obj = var8.next();
                    pss.setValues(ps, obj);
                    ++n;
                    int batchIdx;
                    if (batchSupported) {
                        ps.addBatch();
                        if (n % batchSize == 0 || n == batchArgs.size()) {
                            if (this.logger.isTraceEnabled()) {
                                batchIdx = n % batchSize == 0 ? n / batchSize : n / batchSize + 1;
                                int items = n - (n % batchSize == 0 ? n / batchSize - 1 : n / batchSize) * batchSize;
                                this.logger.trace("Sending SQL batch update #" + batchIdx + " with " + items + " items");
                            }

                            rowsAffected.add(ps.executeBatch());
                        }
                    } else {
                        batchIdx = ps.executeUpdate();
                        rowsAffected.add(new int[]{batchIdx});
                    }
                }

                int[][] result1 = new int[rowsAffected.size()][];

                for(int i = 0; i < result1.length; ++i) {
                    result1[i] = (int[])rowsAffected.get(i);
                }

                int[][] var17 = result1;
                return var17;
            } finally {
                if (pss instanceof ParameterDisposer) {
                    ((ParameterDisposer)pss).cleanupParameters();
                }

            }
        });
        Assert.state(result != null, "No result array");
        return result;
    }

    @Nullable
    public <T> T execute(CallableStatementCreator csc, CallableStatementCallback<T> action) throws DataAccessException {
        Assert.notNull(csc, "CallableStatementCreator must not be null");
        Assert.notNull(action, "Callback object must not be null");
        if (this.logger.isDebugEnabled()) {
            String sql = getSql(csc);
            this.logger.debug("Calling stored procedure" + (sql != null ? " [" + sql + "]" : ""));
        }

        Connection con = DataSourceUtils.getConnection(this.obtainDataSource());
        CallableStatement cs = null;

        Object var13;
        try {
            cs = csc.createCallableStatement(con);
            this.applyStatementSettings(cs);
            T result = action.doInCallableStatement(cs);
            this.handleWarnings((Statement)cs);
            var13 = result;
        } catch (SQLException var10) {
            if (csc instanceof ParameterDisposer) {
                ((ParameterDisposer)csc).cleanupParameters();
            }

            String sql = getSql(csc);
            csc = null;
            JdbcUtils.closeStatement(cs);
            cs = null;
            DataSourceUtils.releaseConnection(con, this.getDataSource());
            con = null;
            throw this.translateException("CallableStatementCallback", sql, var10);
        } finally {
            if (csc instanceof ParameterDisposer) {
                ((ParameterDisposer)csc).cleanupParameters();
            }

            JdbcUtils.closeStatement(cs);
            DataSourceUtils.releaseConnection(con, this.getDataSource());
        }

        return var13;
    }

    @Nullable
    public <T> T execute(String callString, CallableStatementCallback<T> action) throws DataAccessException {
        return this.execute((CallableStatementCreator)(new JdbcTemplate.SimpleCallableStatementCreator(callString)), (CallableStatementCallback)action);
    }

    public Map<String, Object> call(CallableStatementCreator csc, List<SqlParameter> declaredParameters) throws DataAccessException {
        List<SqlParameter> updateCountParameters = new ArrayList();
        List<SqlParameter> resultSetParameters = new ArrayList();
        List<SqlParameter> callParameters = new ArrayList();
        Iterator var6 = declaredParameters.iterator();

        while(var6.hasNext()) {
            SqlParameter parameter = (SqlParameter)var6.next();
            if (parameter.isResultsParameter()) {
                if (parameter instanceof SqlReturnResultSet) {
                    resultSetParameters.add(parameter);
                } else {
                    updateCountParameters.add(parameter);
                }
            } else {
                callParameters.add(parameter);
            }
        }

        Map<String, Object> result = (Map)this.execute(csc, (cs) -> {
            boolean retVal = cs.execute();
            int updateCount = cs.getUpdateCount();
            if (this.logger.isTraceEnabled()) {
                this.logger.trace("CallableStatement.execute() returned '" + retVal + "'");
                this.logger.trace("CallableStatement.getUpdateCount() returned " + updateCount);
            }

            Map<String, Object> resultsMap = this.createResultsMap();
            if (retVal || updateCount != -1) {
                resultsMap.putAll(this.extractReturnedResults(cs, updateCountParameters, resultSetParameters, updateCount));
            }

            resultsMap.putAll(this.extractOutputParameters(cs, callParameters));
            return resultsMap;
        });
        Assert.state(result != null, "No result map");
        return result;
    }

    protected Map<String, Object> extractReturnedResults(CallableStatement cs, @Nullable List<SqlParameter> updateCountParameters, @Nullable List<SqlParameter> resultSetParameters, int updateCount) throws SQLException {
        Map<String, Object> results = new LinkedHashMap(4);
        int rsIndex = 0;
        int updateIndex = 0;
        boolean moreResults;
        if (!this.skipResultsProcessing) {
            do {
                String undeclaredName;
                if (updateCount == -1) {
                    if (resultSetParameters != null && resultSetParameters.size() > rsIndex) {
                        SqlReturnResultSet declaredRsParam = (SqlReturnResultSet)resultSetParameters.get(rsIndex);
                        results.putAll(this.processResultSet(cs.getResultSet(), declaredRsParam));
                        ++rsIndex;
                    } else if (!this.skipUndeclaredResults) {
                        undeclaredName = "#result-set-" + (rsIndex + 1);
                        SqlReturnResultSet undeclaredRsParam = new SqlReturnResultSet(undeclaredName, this.getColumnMapRowMapper());
                        if (this.logger.isTraceEnabled()) {
                            this.logger.trace("Added default SqlReturnResultSet parameter named '" + undeclaredName + "'");
                        }

                        results.putAll(this.processResultSet(cs.getResultSet(), undeclaredRsParam));
                        ++rsIndex;
                    }
                } else if (updateCountParameters != null && updateCountParameters.size() > updateIndex) {
                    SqlReturnUpdateCount ucParam = (SqlReturnUpdateCount)updateCountParameters.get(updateIndex);
                    String declaredUcName = ucParam.getName();
                    results.put(declaredUcName, updateCount);
                    ++updateIndex;
                } else if (!this.skipUndeclaredResults) {
                    undeclaredName = "#update-count-" + (updateIndex + 1);
                    if (this.logger.isTraceEnabled()) {
                        this.logger.trace("Added default SqlReturnUpdateCount parameter named '" + undeclaredName + "'");
                    }

                    results.put(undeclaredName, updateCount);
                    ++updateIndex;
                }

                moreResults = cs.getMoreResults();
                updateCount = cs.getUpdateCount();
                if (this.logger.isTraceEnabled()) {
                    this.logger.trace("CallableStatement.getUpdateCount() returned " + updateCount);
                }
            } while(moreResults || updateCount != -1);
        }

        return results;
    }

    protected Map<String, Object> extractOutputParameters(CallableStatement cs, List<SqlParameter> parameters) throws SQLException {
        Map<String, Object> results = new LinkedHashMap(parameters.size());
        int sqlColIndex = 1;
        Iterator var5 = parameters.iterator();

        while(var5.hasNext()) {
            SqlParameter param = (SqlParameter)var5.next();
            if (param instanceof SqlOutParameter) {
                SqlOutParameter outParam = (SqlOutParameter)param;
                Assert.state(outParam.getName() != null, "Anonymous parameters not allowed");
                SqlReturnType returnType = outParam.getSqlReturnType();
                Object out;
                if (returnType != null) {
                    out = returnType.getTypeValue(cs, sqlColIndex, outParam.getSqlType(), outParam.getTypeName());
                    results.put(outParam.getName(), out);
                } else {
                    out = cs.getObject(sqlColIndex);
                    if (out instanceof ResultSet) {
                        if (outParam.isResultSetSupported()) {
                            results.putAll(this.processResultSet((ResultSet)out, outParam));
                        } else {
                            String rsName = outParam.getName();
                            SqlReturnResultSet rsParam = new SqlReturnResultSet(rsName, this.getColumnMapRowMapper());
                            results.putAll(this.processResultSet((ResultSet)out, rsParam));
                            if (this.logger.isTraceEnabled()) {
                                this.logger.trace("Added default SqlReturnResultSet parameter named '" + rsName + "'");
                            }
                        }
                    } else {
                        results.put(outParam.getName(), out);
                    }
                }
            }

            if (!param.isResultsParameter()) {
                ++sqlColIndex;
            }
        }

        return results;
    }

    protected Map<String, Object> processResultSet(@Nullable ResultSet rs, ResultSetSupportingSqlParameter param) throws SQLException {
        if (rs != null) {
            try {
                if (param.getRowMapper() != null) {
                    RowMapper<?> rowMapper = param.getRowMapper();
                    Object data = (new RowMapperResultSetExtractor(rowMapper)).extractData(rs);
                    Map var5 = Collections.singletonMap(param.getName(), data);
                    return var5;
                }

                Map var4;
                if (param.getRowCallbackHandler() != null) {
                    RowCallbackHandler rch = param.getRowCallbackHandler();
                    (new JdbcTemplate.RowCallbackHandlerResultSetExtractor(rch)).extractData(rs);
                    var4 = Collections.singletonMap(param.getName(), "ResultSet returned from stored procedure was processed");
                    return var4;
                }

                if (param.getResultSetExtractor() != null) {
                    Object data = param.getResultSetExtractor().extractData(rs);
                    var4 = Collections.singletonMap(param.getName(), data);
                    return var4;
                }
            } finally {
                JdbcUtils.closeResultSet(rs);
            }
        }

        return Collections.emptyMap();
    }

    protected RowMapper<Map<String, Object>> getColumnMapRowMapper() {
        return new ColumnMapRowMapper();
    }

    protected <T> RowMapper<T> getSingleColumnRowMapper(Class<T> requiredType) {
        return new SingleColumnRowMapper(requiredType);
    }

    protected Map<String, Object> createResultsMap() {
        return (Map)(this.isResultsMapCaseInsensitive() ? new LinkedCaseInsensitiveMap() : new LinkedHashMap());
    }

    protected void applyStatementSettings(Statement stmt) throws SQLException {
        int fetchSize = this.getFetchSize();
        if (fetchSize != -1) {
            stmt.setFetchSize(fetchSize);
        }

        int maxRows = this.getMaxRows();
        if (maxRows != -1) {
            stmt.setMaxRows(maxRows);
        }

        DataSourceUtils.applyTimeout(stmt, this.getDataSource(), this.getQueryTimeout());
    }

    protected PreparedStatementSetter newArgPreparedStatementSetter(@Nullable Object[] args) {
        return new ArgumentPreparedStatementSetter(args);
    }

    protected PreparedStatementSetter newArgTypePreparedStatementSetter(Object[] args, int[] argTypes) {
        return new ArgumentTypePreparedStatementSetter(args, argTypes);
    }

    protected void handleWarnings(Statement stmt) throws SQLException {
        if (this.isIgnoreWarnings()) {
            if (this.logger.isDebugEnabled()) {
                for(SQLWarning warningToLog = stmt.getWarnings(); warningToLog != null; warningToLog = warningToLog.getNextWarning()) {
                    this.logger.debug("SQLWarning ignored: SQL state '" + warningToLog.getSQLState() + "', error code '" + warningToLog.getErrorCode() + "', message [" + warningToLog.getMessage() + "]");
                }
            }
        } else {
            this.handleWarnings(stmt.getWarnings());
        }

    }

    protected void handleWarnings(@Nullable SQLWarning warning) throws SQLWarningException {
        if (warning != null) {
            throw new SQLWarningException("Warning not ignored", warning);
        }
    }

	// 异常转换 DataAccessException UncategorizedSQLException都是RuntimeException的子类
    protected DataAccessException translateException(String task, @Nullable String sql, SQLException ex) {
        DataAccessException dae = this.getExceptionTranslator().translate(task, sql, ex);
        return (DataAccessException)(dae != null ? dae : new UncategorizedSQLException(task, sql, ex));
    }

    @Nullable
    private static String getSql(Object sqlProvider) {
        return sqlProvider instanceof SqlProvider ? ((SqlProvider)sqlProvider).getSql() : null;
    }

    private static <T> T result(@Nullable T result) {
        Assert.state(result != null, "No result");
        return result;
    }

    private static int updateCount(@Nullable Integer result) {
        Assert.state(result != null, "No update count");
        return result;
    }

    private static class RowCallbackHandlerResultSetExtractor implements ResultSetExtractor<Object> {
        private final RowCallbackHandler rch;

        public RowCallbackHandlerResultSetExtractor(RowCallbackHandler rch) {
            this.rch = rch;
        }

        @Nullable
        public Object extractData(ResultSet rs) throws SQLException {
            while(rs.next()) {
                this.rch.processRow(rs);
            }

            return null;
        }
    }

    private static class SimpleCallableStatementCreator implements CallableStatementCreator, SqlProvider {
        private final String callString;

        public SimpleCallableStatementCreator(String callString) {
            Assert.notNull(callString, "Call string must not be null");
            this.callString = callString;
        }

        public CallableStatement createCallableStatement(Connection con) throws SQLException {
            return con.prepareCall(this.callString);
        }

        public String getSql() {
            return this.callString;
        }
    }

    private static class SimplePreparedStatementCreator implements PreparedStatementCreator, SqlProvider {
        private final String sql;

        public SimplePreparedStatementCreator(String sql) {
            Assert.notNull(sql, "SQL must not be null");
            this.sql = sql;
        }

        public PreparedStatement createPreparedStatement(Connection con) throws SQLException {
            return con.prepareStatement(this.sql);
        }

        public String getSql() {
            return this.sql;
        }
    }

    private class CloseSuppressingInvocationHandler implements InvocationHandler {
        private final Connection target;

        public CloseSuppressingInvocationHandler(Connection target) {
            this.target = target;
        }

        @Nullable
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("equals")) {
                return proxy == args[0];
            } else if (method.getName().equals("hashCode")) {
                return System.identityHashCode(proxy);
            } else {
                if (method.getName().equals("unwrap")) {
                    if (((Class)args[0]).isInstance(proxy)) {
                        return proxy;
                    }
                } else if (method.getName().equals("isWrapperFor")) {
                    if (((Class)args[0]).isInstance(proxy)) {
                        return true;
                    }
                } else {
                    if (method.getName().equals("close")) {
                        return null;
                    }

                    if (method.getName().equals("isClosed")) {
                        return false;
                    }

                    if (method.getName().equals("getTargetConnection")) {
                        return this.target;
                    }
                }

                try {
                    Object retVal = method.invoke(this.target, args);
                    if (retVal instanceof Statement) {
                        JdbcTemplate.this.applyStatementSettings((Statement)retVal);
                    }

                    return retVal;
                } catch (InvocationTargetException var5) {
                    throw var5.getTargetException();
                }
            }
        }
    }
}
