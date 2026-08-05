package com.microcourse.util;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * PostgreSQL jsonb 列专用 TypeHandler：实体字段为 JSON 字符串。
 *
 * <p>背景：{@code JacksonTypeHandler} 对 String 字段写入时按 varchar 绑定，
 * 而 jsonb 列要求以 OTHER 类型绑定（否则报
 * {@code column "pending_courses" is of type jsonb but expression is of type character varying}）。
 * 本 Handler 在写入时以 {@code Types.OTHER} 绑定，读取时按字符串返回，解决班级导入等
 * 写入 pending_courses 的场景。
 */
public class JsonbStringTypeHandler extends BaseTypeHandler<String> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setObject(i, parameter, Types.OTHER);
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        Object value = rs.getObject(columnName);
        return value == null ? null : value.toString();
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        Object value = rs.getObject(columnIndex);
        return value == null ? null : value.toString();
    }

    @Override
    public String getNullableResult(java.sql.CallableStatement cs, int columnIndex) throws SQLException {
        Object value = cs.getObject(columnIndex);
        return value == null ? null : value.toString();
    }
}
