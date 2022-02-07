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
package org.apache.ibatis.type;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Clinton Begin
 *
 * 用于完成单个参数与单个列值的类型转换。
 *
 * PreparedStatement为SQL语句绑定参数时，需要从Java类型转换成JDBC类型，从结果集中获取数据时，需要从JDBC类型转换成Java类型。
 */
public interface TypeHandler<T> {

    // 为SQL绑定数据，从JDBC类型转换成Java类型
    void setParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) throws SQLException;

    /**
     * Gets the result.
     *
     * @param rs         the rs
     * @param columnName Column name, when configuration <code>useColumnLabel</code> is <code>false</code>
     * @return the result
     * @throws SQLException the SQL exception
     *
     * 从ResultSet中获取数据，从Java类型转换成JDBC类型
     */
    T getResult(ResultSet rs, String columnName) throws SQLException;

    T getResult(ResultSet rs, int columnIndex) throws SQLException;

    T getResult(CallableStatement cs, int columnIndex) throws SQLException;

}
