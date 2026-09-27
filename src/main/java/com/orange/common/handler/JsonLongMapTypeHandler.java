package com.orange.common.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedJdbcTypes;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code Map<String, Long>} 与 JSON 字符串互转的类型处理器
 *
 * <p>用于把「取值分布」这类 {@code {档位: 计数}} 结构以 VARCHAR 列存进数据库。相比
 * MyBatis-Plus 自带的 {@code JacksonTypeHandler}，这里用 {@link TypeReference} 显式固定了泛型，
 * 取值一律反序列化为 {@code Long}，不依赖框架对字段泛型的解析能力：Jackson 在没有泛型信息时
 * 会按数值大小把计数反序列化成 {@code Integer}，取用时会在 {@code for (Long v : ...)} 处抛
 * {@code ClassCastException}，且只在运行期才暴露。</p>
 *
 * <p>空列按空分布处理，返回空 Map，与目标表分布列的 NOT NULL 约束配套使用。</p>
 *
 * @author UserCenter
 */
@MappedTypes(Map.class)
@MappedJdbcTypes(JdbcType.VARCHAR)
public class JsonLongMapTypeHandler extends BaseTypeHandler<Map<String, Long>> {

    /** JSON 处理器，无状态、线程安全 */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 固定泛型的反序列化目标，保证 value 是 Long 而不是 Integer */
    private static final TypeReference<LinkedHashMap<String, Long>> MAP_TYPE =
            new TypeReference<LinkedHashMap<String, Long>>() {
            };

    /**
     * 写入数据库：把取值分布序列化为 JSON 字符串
     *
     * @param ps        预编译语句
     * @param i         参数下标
     * @param parameter 取值分布，MyBatis 保证非空
     * @param jdbcType  JDBC 类型
     * @throws SQLException 写入失败时抛出
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Map<String, Long> parameter,
                                   JdbcType jdbcType) throws SQLException {
        ps.setString(i, toJson(parameter));
    }

    /**
     * 读取数据库（按列名）
     *
     * @param rs         结果集
     * @param columnName 列名
     * @return 取值分布，空列返回空 Map
     * @throws SQLException 读取失败时抛出
     */
    @Override
    public Map<String, Long> getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return fromJson(rs.getString(columnName));
    }

    /**
     * 读取数据库（按列下标）
     *
     * @param rs          结果集
     * @param columnIndex 列下标
     * @return 取值分布，空列返回空 Map
     * @throws SQLException 读取失败时抛出
     */
    @Override
    public Map<String, Long> getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return fromJson(rs.getString(columnIndex));
    }

    /**
     * 读取存储过程出参
     *
     * @param cs          可调用语句
     * @param columnIndex 参数下标
     * @return 取值分布，空值返回空 Map
     * @throws SQLException 读取失败时抛出
     */
    @Override
    public Map<String, Long> getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return fromJson(cs.getString(columnIndex));
    }

    /**
     * 取值分布转 JSON 字符串
     *
     * @param distribution 取值分布
     * @return JSON 字符串
     */
    private static String toJson(Map<String, Long> distribution) {
        try {
            return OBJECT_MAPPER.writeValueAsString(distribution);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("取值分布序列化失败", e);
        }
    }

    /**
     * JSON 字符串转取值分布
     *
     * @param json 数据库列内容，允许为空
     * @return 取值分布，空内容返回空 Map
     */
    private static Map<String, Long> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("取值分布反序列化失败：" + json, e);
        }
    }
}
