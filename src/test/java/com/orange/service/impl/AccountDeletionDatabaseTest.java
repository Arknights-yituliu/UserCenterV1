package com.orange.service.impl;

import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.orange.mapper.AccountDeletionMapper;
import com.orange.mapper.AkAccountBindingMapper;
import com.orange.mapper.EfAccountBindingMapper;
import com.orange.mapper.UserInfoMapper;
import com.orange.service.AccountDeletionService;
import com.orange.service.OAuthTokenService;
import com.orange.service.RevokeService;
import com.orange.service.EmailCodeService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 内存数据库验证真实 Mapper、事务回滚及游戏数据保留，不连接部署环境。 */
class AccountDeletionDatabaseTest {
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate jdbc;
    private AccountDeletionService service;
    private static final List<String> UID_TABLES = List.of("ak_default_source", "user_config",
            "user_config_quota", "user_schedule", "oauth_user_scope", "oauth_grant",
            "oauth_custom_token", "tlc_pat", "login_log");

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext(TestConfig.class);
        jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        service = context.getBean(AccountDeletionService.class);
        jdbc.execute("SET MODE MySQL");
        jdbc.execute("CREATE TABLE user_info (uid BIGINT PRIMARY KEY, email VARCHAR(128), user_name VARCHAR(64), "
                + "password VARCHAR(128), avatar VARCHAR(512), nickname VARCHAR(64), status INT DEFAULT 1 NOT NULL, "
                + "ip VARCHAR(64), register_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL, last_login_time TIMESTAMP)");
        jdbc.execute("CREATE TABLE user_deletion_audit (uid BIGINT PRIMARY KEY, email VARCHAR(128), user_name VARCHAR(64), "
                + "avatar VARCHAR(512), nickname VARCHAR(64), status INT NOT NULL, ip VARCHAR(64), "
                + "register_time TIMESTAMP NOT NULL, last_login_time TIMESTAMP, deleted_at TIMESTAMP NOT NULL, "
                + "auth_type VARCHAR(32) NOT NULL, client_id VARCHAR(64))");
        jdbc.execute("CREATE TABLE oauth_client (owner_uid BIGINT)");
        jdbc.execute("CREATE TABLE ak_account_binding (ak_uid VARCHAR(32), owner_uid BIGINT, "
                + "channel_name VARCHAR(64), channel_master_id VARCHAR(64), nick_name VARCHAR(64), "
                + "update_time TIMESTAMP, UNIQUE(ak_uid, owner_uid))");
        jdbc.execute("CREATE TABLE ef_account_binding (role_id VARCHAR(64), server_id VARCHAR(64), owner_uid BIGINT, "
                + "hg_uid VARCHAR(64), nick_name VARCHAR(64), server_type VARCHAR(64), server_name VARCHAR(64), "
                + "player_data_imported INT, pool_record_imported INT, update_time TIMESTAMP, "
                + "UNIQUE(role_id, server_id, owner_uid))");
        jdbc.execute("CREATE TABLE audit_log (operator_id BIGINT, operator_type VARCHAR(16))");
        for (String table : UID_TABLES) {
            jdbc.execute("CREATE TABLE " + table + " (uid BIGINT)");
            jdbc.update("INSERT INTO " + table + " VALUES (9), (10)");
        }
        jdbc.update("INSERT INTO user_info (uid, email, user_name, password, nickname, ip) "
                + "VALUES (9,'old@example.com','old-user','secret-password-hash','Old nickname','127.0.0.9'), "
                + "(10,'other@example.com','other-user','other-password-hash','Other nickname','127.0.0.10')");
        jdbc.update("INSERT INTO oauth_client VALUES (9)");
        jdbc.update("INSERT INTO ak_account_binding (ak_uid, owner_uid) VALUES ('game',9), ('game',10)");
        jdbc.update("INSERT INTO ef_account_binding (role_id, server_id, owner_uid) "
                + "VALUES ('role','server',9), ('role','server',10)");
        jdbc.update("INSERT INTO audit_log VALUES (9,'user'), (10,'user'), (9,'admin')");
        // 包括按 uid 归属的手动游戏数据，注销也不能修改。
        for (String table : List.of("ak_operator_state", "ak_operator_state_manual", "ef_player_data",
                "ef_character_pool_record", "ef_weapon_pool_record")) {
            jdbc.execute("CREATE TABLE " + table + " (data VARCHAR(32))");
            jdbc.update("INSERT INTO " + table + " VALUES ('keep')");
        }
    }

    @AfterEach
    void tearDown() {
        jdbc.execute("DROP ALL OBJECTS");
        context.close();
    }

    @Test
    void archivesUserAndPreservesAllOtherBusinessAndGameData() {
        service.deleteAccount(9L, "123456");
        assertEquals(0, count("user_info", "uid=9"));
        assertEquals(1, count("user_info", "uid=10"));
        for (String table : UID_TABLES) {
            assertEquals(1, count(table, "uid=9"), table);
            assertEquals(1, count(table, "uid=10"), table);
        }
        assertEquals(0, count("ak_account_binding", "owner_uid=9"));
        assertEquals(1, count("ak_account_binding", "owner_uid=10"));
        assertEquals(0, count("ef_account_binding", "owner_uid=9"));
        assertEquals(1, count("ef_account_binding", "owner_uid=10"));
        assertEquals(1, count("oauth_client", "owner_uid=9"));
        assertEquals(1, count("audit_log", "operator_id=9 AND operator_type='user'"));
        assertEquals(1, count("audit_log", "operator_id=9 AND operator_type='admin'"));
        assertEquals(1, count("user_deletion_audit", "uid=9"));
        assertEquals(0, count("user_deletion_audit", "uid=10"));
        assertEquals("old@example.com", jdbc.queryForObject("SELECT email FROM user_deletion_audit WHERE uid=9", String.class));
        assertEquals("old-user", jdbc.queryForObject("SELECT user_name FROM user_deletion_audit WHERE uid=9", String.class));
        assertEquals("Old nickname", jdbc.queryForObject("SELECT nickname FROM user_deletion_audit WHERE uid=9", String.class));
        assertEquals("127.0.0.9", jdbc.queryForObject("SELECT ip FROM user_deletion_audit WHERE uid=9", String.class));
        assertEquals("session", jdbc.queryForObject("SELECT auth_type FROM user_deletion_audit WHERE uid=9", String.class));
        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM user_deletion_audit WHERE uid=9", java.sql.Timestamp.class));
        verify(context.getBean(RevokeService.class)).kickAllSessions(9L);
        verify(context.getBean(RevokeService.class), never()).revokeUserTokens(9L);
        for (String table : List.of("ak_operator_state", "ak_operator_state_manual", "ef_player_data",
                "ef_character_pool_record", "ef_weapon_pool_record")) {
            assertEquals("keep", jdbc.queryForObject("SELECT data FROM " + table, String.class));
        }
    }

    @Test
    void finalAccountDeleteFailureRollsBackEarlierDatabaseCleanup() {
        jdbc.execute("CREATE TABLE deletion_failure (uid BIGINT REFERENCES user_info(uid))");
        jdbc.update("INSERT INTO deletion_failure VALUES (9)");
        assertThrows(RuntimeException.class, () -> service.deleteAccount(9L, "123456"));
        assertEquals(1, count("user_info", "uid=9"));
        assertEquals(1, count("ak_account_binding", "owner_uid=9"));
        assertEquals(1, count("ef_account_binding", "owner_uid=9"));
        assertEquals(0, count("user_deletion_audit", "uid=9"));
        verifyNoInteractions(context.getBean(RevokeService.class));
        for (String table : UID_TABLES) {
            assertEquals(1, count(table, "uid=9"), table);
        }
    }

    @Test
    void auditInsertFailureLeavesAccountAndBindingsUntouched() {
        jdbc.execute("ALTER TABLE user_deletion_audit ADD CONSTRAINT reject_user CHECK (uid <> 9)");
        assertThrows(RuntimeException.class, () -> service.deleteAccount(9L, "123456"));
        assertEquals(1, count("user_info", "uid=9"));
        assertEquals(1, count("ak_account_binding", "owner_uid=9"));
        assertEquals(1, count("ef_account_binding", "owner_uid=9"));
        assertEquals(0, count("user_deletion_audit", "uid=9"));
        verifyNoInteractions(context.getBean(RevokeService.class));
    }

    @Test
    void sessionCleanupFailureRollsBackAuditAndAllDatabaseDeletes() {
        doThrow(new IllegalStateException("redis unavailable"))
                .when(context.getBean(RevokeService.class)).kickAllSessions(9L);
        assertThrows(IllegalStateException.class, () -> service.deleteAccount(9L, "123456"));
        assertEquals(1, count("user_info", "uid=9"));
        assertEquals(1, count("ak_account_binding", "owner_uid=9"));
        assertEquals(1, count("ef_account_binding", "owner_uid=9"));
        assertEquals(0, count("user_deletion_audit", "uid=9"));
    }

    @Test
    void cannotOverwriteExistingAuditAndDeleteAgain() {
        service.deleteAccount(9L, "123456");
        assertThrows(RuntimeException.class, () -> service.deleteAccount(9L, "123456"));
        assertEquals(1, count("user_deletion_audit", "uid=9"));
        assertEquals("old@example.com", jdbc.queryForObject("SELECT email FROM user_deletion_audit WHERE uid=9", String.class));
    }

    @Test
    void delayedImportsCannotRecreateBindingsForDeletedAccount() {
        service.deleteAccount(9L, "123456");
        SqlSessionTemplate session = context.getBean(SqlSessionTemplate.class);
        AkAccountBindingMapper ak = session.getMapper(AkAccountBindingMapper.class);
        EfAccountBindingMapper ef = session.getMapper(EfAccountBindingMapper.class);
        assertEquals(0, ak.upsertBinding("late", 9L, null, null, null));
        assertEquals(0, ef.upsertBinding("late", "server", 9L, null, null));
        assertEquals(0, ef.upsertPoolRecordBinding("late", 9L, "hg", "nickname"));
        assertEquals(1, ak.upsertBinding("active", 10L, null, null, null));
        assertEquals(1, ef.upsertBinding("active", "server", 10L, null, null));
        assertEquals(1, ef.upsertPoolRecordBinding("active", 10L, "hg", "nickname"));
    }

    private int count(String table, String condition) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + condition, Integer.class);
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }
        @Bean PlatformTransactionManager transactions(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean SqlSessionFactory sessionFactory(DataSource source) throws Exception {
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(source);
            SqlSessionFactory sessionFactory = factory.getObject();
            sessionFactory.getConfiguration().addMapper(UserInfoMapper.class);
            sessionFactory.getConfiguration().addMapper(AccountDeletionMapper.class);
            sessionFactory.getConfiguration().addMapper(AkAccountBindingMapper.class);
            sessionFactory.getConfiguration().addMapper(EfAccountBindingMapper.class);
            return sessionFactory;
        }
        @Bean SqlSessionTemplate sessions(SqlSessionFactory factory) { return new SqlSessionTemplate(factory); }
        @Bean RevokeService revoke() { return mock(RevokeService.class); }
        @Bean EmailCodeService emailCodes() { return mock(EmailCodeService.class); }
        @Bean AccountDeletionService service(SqlSessionTemplate session, RevokeService revoke, EmailCodeService emailCodes) {
            return new AccountDeletionServiceImpl(session.getMapper(UserInfoMapper.class),
                    session.getMapper(AccountDeletionMapper.class), mock(OAuthTokenService.class),
                    revoke, emailCodes);
        }
    }
}
