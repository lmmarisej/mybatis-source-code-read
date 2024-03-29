/*
 *    Copyright 2009-2021 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.logging.jdbc;

import org.apache.ibatis.logging.Log;
import org.apache.ibatis.reflection.ExceptionUtil;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashSet;
import java.util.Set;
import java.util.StringJoiner;

/**
 * ResultSet proxy to add logging.
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public final class ResultSetLogger extends BaseJdbcLogger implements InvocationHandler {

    private static final Set<Integer> BLOB_TYPES = new HashSet<>();     // 超大长度类型
    private boolean first = true;       // ResultSet结果集的第一行
    private int rows;       // 统计行数
    private final ResultSet rs;     // 真正的ResultSet对象
    private final Set<Integer> blobColumns = new HashSet<>();       // 超大字段的列编号

    static {
        BLOB_TYPES.add(Types.BINARY);
        BLOB_TYPES.add(Types.BLOB);
        BLOB_TYPES.add(Types.CLOB);
        BLOB_TYPES.add(Types.LONGNVARCHAR);
        BLOB_TYPES.add(Types.LONGVARBINARY);
        BLOB_TYPES.add(Types.LONGVARCHAR);
        BLOB_TYPES.add(Types.NCLOB);
        BLOB_TYPES.add(Types.VARBINARY);
    }

    private ResultSetLogger(ResultSet rs, Log statementLog, int queryStack) {
        super(statementLog, queryStack);
        this.rs = rs;
    }

    // 针对ResultSet的next调用做一些后置操作
    @Override
    public Object invoke(Object proxy, Method method, Object[] params) throws Throwable {
        try {
            if (Object.class.equals(method.getDeclaringClass())) {
                return method.invoke(this, params);
            }
            Object o = method.invoke(rs, params);
            if ("next".equals(method.getName())) {      // 本次调用是next方法
                if ((Boolean) o) {      // next是否返回了结果
                    rows++;
                    if (isTraceEnabled()) {
                        ResultSetMetaData rsmd = rs.getMetaData();
                        final int columnCount = rsmd.getColumnCount();      // 数据集的列数
                        if (first) {
                            first = false;
                            printColumnHeaders(rsmd, columnCount);      // 第一场会输出表头，填充blobColumns集合，记录超大类型的列
                        }
                        printColumnValues(columnCount);     // 输出改行记录，blobColumns列中的数据过大，会将其过滤，不会输出
                    }
                }
                // 没有结果了，说明到了最后
                else {
                    debug("     Total: " + rows, false);        // 遍历完ResultSet后，输出总函数
                }
            }
            clearColumnInfo();      // 清空
            return o;
        } catch (Throwable t) {
            throw ExceptionUtil.unwrapThrowable(t);
        }
    }

    private void printColumnHeaders(ResultSetMetaData rsmd, int columnCount) throws SQLException {
        StringJoiner row = new StringJoiner(", ", "   Columns: ", "");
        for (int i = 1; i <= columnCount; i++) {
            if (BLOB_TYPES.contains(rsmd.getColumnType(i))) {
                blobColumns.add(i);
            }
            row.add(rsmd.getColumnLabel(i));
        }
        trace(row.toString(), false);
    }

    private void printColumnValues(int columnCount) {
        StringJoiner row = new StringJoiner(", ", "       Row: ", "");
        for (int i = 1; i <= columnCount; i++) {
            try {
                if (blobColumns.contains(i)) {
                    row.add("<<BLOB>>");
                } else {
                    row.add(rs.getString(i));
                }
            } catch (SQLException e) {
                // generally can't call getString() on a BLOB column
                row.add("<<Cannot Display>>");
            }
        }
        trace(row.toString(), false);
    }

    /**
     * Creates a logging version of a ResultSet.
     *
     * @param rs           the ResultSet to proxy
     * @param statementLog the statement log
     * @param queryStack   the query stack
     * @return the ResultSet with logging
     */
    public static ResultSet newInstance(ResultSet rs, Log statementLog, int queryStack) {
        InvocationHandler handler = new ResultSetLogger(rs, statementLog, queryStack);
        ClassLoader cl = ResultSet.class.getClassLoader();
        return (ResultSet) Proxy.newProxyInstance(cl, new Class[]{ResultSet.class}, handler);
    }

    /**
     * Get the wrapped result set.
     *
     * @return the resultSet
     */
    public ResultSet getRs() {
        return rs;
    }

}
