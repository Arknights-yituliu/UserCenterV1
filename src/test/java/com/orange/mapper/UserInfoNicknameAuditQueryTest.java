package com.orange.mapper;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** 在无数据库的情况下验证动态 SQL 能解析日界线和键集翻页条件。 */
class UserInfoNicknameAuditQueryTest {

    @Test
    void firstPageUsesHalfOpenDayAndExcludesAlreadyPersistedUids() {
        BoundSql statement = statement(null);
        String sql = statement.getSql().replaceAll("\\s+", " ");

        assertThat(sql).contains("u.register_time >= ?", "u.register_time < ?",
                "NOT EXISTS (SELECT 1 FROM user_nickname_audit a WHERE a.uid = u.uid)",
                "ORDER BY u.uid ASC", "LIMIT ?");
        assertThat(sql).doesNotContain("u.uid > ?");
        assertThat(statement.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .containsExactly("startTime", "endTime", "limit");
    }

    @Test
    void laterPagesUseUidRatherThanOffsetAfterInsertedRowsDisappearFromPendingSet() {
        BoundSql statement = statement(123L);

        assertThat(statement.getSql().replaceAll("\\s+", " ")).contains("AND u.uid > ?").doesNotContain("OFFSET");
        assertThat(statement.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .containsExactly("startTime", "endTime", "afterUid", "limit");
    }

    private BoundSql statement(Long afterUid) {
        Configuration configuration = new Configuration();
        configuration.addMapper(UserInfoMapper.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("startTime", LocalDate.of(2026, 11, 14).atStartOfDay());
        parameters.put("endTime", LocalDate.of(2026, 11, 15).atStartOfDay());
        parameters.put("afterUid", afterUid);
        parameters.put("limit", 200);
        return configuration.getMappedStatement(UserInfoMapper.class.getName() + ".selectUnauditedNicknames")
                .getBoundSql(parameters);
    }
}
