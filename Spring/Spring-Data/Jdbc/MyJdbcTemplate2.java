package com.gb.soa.omp.cpromotion.util;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;
import org.springframework.jdbc.support.rowset.SqlRowSet;

import java.util.Collection;
import java.util.List;
import java.util.Map;


/**
 * 封装JdbcTemplate常用的方法
 */
public class MyJdbcTemplate2 extends JdbcTemplate {

    private String dbAnnotatePrefix = "";

    public MyJdbcTemplate2() {
    }

    public String getDbAnnotatePrefix() {
        return this.dbAnnotatePrefix;
    }

    public void setDbAnnotatePrefix(String dbAnnotatePrefix) {
        this.dbAnnotatePrefix = dbAnnotatePrefix;
    }

    public String replaceSql(String sql) {
        return this.dbAnnotatePrefix + sql;
    }

    /**
     * 单列查询(返回null不报错)
     * @param sql           sql
     * @param args          参数
     * @param requiredType  列值类型(基本类型的包装类型) 不是用来处理javabean的
     * @param <T>
     * @return
     * @throws DataAccessException
     */
    public <T> T queryForObject(String sql, Object[] args, Class<T> requiredType) throws DataAccessException {
        sql = this.replaceSql(sql);
        return this.queryForObject(sql, args, this.getSingleColumnRowMapper(requiredType));
    }

    /**
     * 单列查询(返回null不报错)
     *      默认的JdbcTemplate的queryForObject方法查询结果为空/0条记录时会抛出异常
     * @param sql       sql
     * @param args      参数
     * @param rowMapper 行映射器(可以处理特殊类型) 不是用来处理javabean的
     * @param <T>
     * @return
     * @throws DataAccessException
     */
    public <T> T queryForObject(String sql, Object[] args, RowMapper<T> rowMapper) throws DataAccessException {
        sql = this.replaceSql(sql);
        List<T> results = (List)super.query(sql, args, new RowMapperResultSetExtractor(rowMapper, 1));
        return IDataAccessUtils.requiredSingleResult(results);
    }

    /**
     * 查询列表
     * @param sql       sql
     * @param args      参数
     * @param rowMapper 行映射器
     * @param <T>
     * @return
     * @throws DataAccessException
     */
    public <T> List<T> query(String sql, Object[] args, RowMapper<T> rowMapper) throws DataAccessException {
        sql = this.replaceSql(sql);
        return super.query(sql, args, rowMapper);
    }

    /**
     * 查询列表
     * @param sql       sql
     * @param args      参数
     * @param beanType  结果封装成bean类型
     * @param <T>
     * @return
     * @throws DataAccessException
     */
    public <T> List<T> queryForBean(String sql, Object[] args, Class<T> beanType) throws DataAccessException {
        sql = this.replaceSql(sql);
        return super.query(sql, args, new BeanPropertyRowMapper<>(beanType));
    }

    /**
     * 查询单列集合
     * @param sql           sql
     * @param args          参数
     * @param elementType   列值类型 不是用来处理javabean的
     * @param <T>
     * @return
     * @throws DataAccessException
     */
    public <T> List<T> queryForList(String sql, Object[] args, Class<T> elementType) throws DataAccessException {
        sql = this.replaceSql(sql);

//        return super.query(sql, args, new RowMapper<T>(){
//            public T mapRow(ResultSet rs, int rowNum) throws SQLException {
//                return (T)rs.getObject(1);
//            }
//        });

        return super.queryForList(sql, args, elementType);
    }

    /**
     * 查询列表
     * @param sql
     * @param args
     * @return
     * @throws DataAccessException
     */
    public List<Map<String, Object>> queryForList(String sql, Object... args) throws DataAccessException {
        sql = this.replaceSql(sql);
        return super.queryForList(sql, args);
    }

    /**
     * 查询行集合
     * @param sql
     * @param args
     * @return
     * @throws DataAccessException
     */
    public SqlRowSet queryForRowSet(String sql, Object... args) throws DataAccessException {
        sql = this.replaceSql(sql);
        return super.queryForRowSet(sql, args);
    }

    /**
     * 更新
     * @param sql
     * @param args
     * @return
     * @throws DataAccessException
     */
    public int update(String sql, Object... args) throws DataAccessException {
        sql = this.replaceSql(sql);
        return super.update(sql, args);
    }

    /**
     * 批量更新
     * @param sql
     * @param batchArgs
     * @return
     * @throws DataAccessException
     */
    public int[] batchUpdate(String sql, List<Object[]> batchArgs) throws DataAccessException {
        return super.batchUpdate(sql, batchArgs);
    }

    static class IDataAccessUtils {
        IDataAccessUtils() {
        }

        public static <T> T requiredSingleResult(Collection<T> results) throws IncorrectResultSizeDataAccessException {
            int size = results != null ? results.size() : 0;
            if (size == 0) {
                // 数据为空时不报错
                return null;
            } else if (results.size() > 1) {
                throw new IncorrectResultSizeDataAccessException(1, size);
            } else {
                return results.iterator().next();
            }
        }
    }

}
