package com.orange.migration;

import com.baomidou.mybatisplus.core.toolkit.AES;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 一次性迁移工具：BackEndV3.user_info → UserCenterV1.user_info
 *
 * <p>字段映射规则：</p>
 * <ul>
 *     <li>uid：新生成雪花 ID（BackEndV3 只有自增 id，无对外 uid）</li>
 *     <li>user_name：原 userName 原样保留（UserCenterV1 登录支持邮箱或用户名）</li>
 *     <li>password：$2 开头的 BCrypt 哈希原样保留；旧 AES 密文用 BackEndV3 的 encryption.secret 解密后重新 BCrypt</li>
 *     <li>nickname：取原 userName（BackEndV3 无独立昵称）</li>
 *     <li>status：原 1（正常）→ 1；其余（含 0 封禁）→ -1（UserCenterV1 封禁为负数）</li>
 *     <li>create_time → register_time；last_login_time 置空（迁移后旧会话失效，重新登录）</li>
 *     <li>delete_flag = 1（已删除）的用户跳过，不迁移</li>
 * </ul>
 *
 * <p>运行方式（参数顺序）：</p>
 * <pre>
 * java com.orange.migration.UserMigrationRunner \
 *   &lt;源库JDBC&gt; &lt;源库用户&gt; &lt;源库密码&gt; \
 *   &lt;目标库JDBC&gt; &lt;目标库用户&gt; &lt;目标库密码&gt; &lt;aesSecret&gt;
 * </pre>
 * <p>aesSecret 即 BackEndV3 配置中的 encryption.secret。</p>
 *
 * @author UserCenter
 */
public class UserMigrationRunner {

    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    /** 源表列（BackEndV3） */
    private static final String SOURCE_SQL =
            "SELECT id, user_name, pass_word, email, create_time, ip, status, avatar, delete_flag FROM user_info";

    /** 目标表列（UserCenterV1），id 自增由数据库生成 */
    private static final String TARGET_SQL =
            "INSERT INTO user_info (uid, email, user_name, password, avatar, nickname, status, ip, register_time, last_login_time) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NULL)";

    private int migrated;
    private int skippedDeleted;
    private int skippedConflict;
    private int legacyAesCount;

    /**
     * 入口方法
     *
     * @param args 7 个参数：源库 JDBC/用户/密码、目标库 JDBC/用户/密码、AES Secret
     */
    public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            System.err.println("用法：UserMigrationRunner <源JDBC> <源用户> <源密码> <目标JDBC> <目标用户> <目标密码> <aesSecret>");
            System.exit(1);
        }
        new UserMigrationRunner().run(args);
    }

    /**
     * 执行迁移主流程
     *
     * @param args 命令行参数
     * @throws Exception 数据库/加解密异常
     */
    private void run(String[] args) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String aesSecret = args[6];

        try (Connection source = DriverManager.getConnection(args[0], args[1], args[2]);
             Connection target = DriverManager.getConnection(args[3], args[4], args[5])) {
            // 幂等保护：目标表已存在迁移数据时中止，防止重复迁移
            try (PreparedStatement countStmt = target.prepareStatement(
                    "SELECT COUNT(*) FROM user_info WHERE user_name IS NOT NULL");
                 ResultSet countRs = countStmt.executeQuery()) {
                countRs.next();
                if (countRs.getLong(1) > 0) {
                    System.err.println("目标表已存在 user_name 数据，疑似重复迁移，已中止。确认后请清空数据重试。");
                    return;
                }
            }

            target.setAutoCommit(false);
            try (PreparedStatement srcStmt = source.prepareStatement(SOURCE_SQL);
                 ResultSet rs = srcStmt.executeQuery();
                 PreparedStatement targetStmt = target.prepareStatement(TARGET_SQL)) {
                while (rs.next()) {
                    migrateRow(rs, targetStmt, aesSecret);
                }
                target.commit();
            } catch (Exception e) {
                target.rollback();
                throw e;
            }
        }

        System.out.println("迁移完成：成功 " + migrated + " 条，跳过已删除 " + skippedDeleted
                + " 条，跳过唯一键冲突 " + skippedConflict + " 条，其中旧 AES 密码重哈希 " + legacyAesCount + " 条");
    }

    /**
     * 转换并插入一行用户数据
     *
     * @param rs         源结果集
     * @param targetStmt 目标表预处理语句
     * @param aesSecret  BackEndV3 的 encryption.secret
     * @throws Exception SQL/加解密异常
     */
    private void migrateRow(ResultSet rs, PreparedStatement targetStmt, String aesSecret) throws Exception {
        // 已删除用户跳过
        if (rs.getBoolean("delete_flag")) {
            skippedDeleted++;
            return;
        }

        String userName = rs.getString("user_name");
        String email = rs.getString("email");
        String rawPassword = rs.getString("pass_word");
        Timestamp createTime = rs.getTimestamp("create_time");
        String ip = rs.getString("ip");
        Integer status = rs.getObject("status") == null ? 1 : rs.getInt("status");
        String avatar = rs.getString("avatar");

        // 密码转换：BCrypt 原样保留；旧 AES 密文解密后重新 BCrypt
        String password = null;
        if (rawPassword != null && !rawPassword.isEmpty()) {
            if (rawPassword.startsWith("$2")) {
                password = rawPassword;
            } else {
                try {
                    String plain = AES.decrypt(rawPassword, aesSecret);
                    password = ENCODER.encode(plain);
                    legacyAesCount++;
                } catch (Exception e) {
                    System.err.println("AES 解密失败（该用户密码置空，需重置）：user_name=" + userName + "，原因：" + e.getMessage());
                }
            }
        }

        int i = 1;
        targetStmt.setLong(i++, IdWorker.getId());            // uid
        targetStmt.setString(i++, email);                     // email
        targetStmt.setString(i++, userName);                  // user_name
        targetStmt.setString(i++, password);                  // password
        targetStmt.setString(i++, avatar);                    // avatar
        targetStmt.setString(i++, userName);                  // nickname
        targetStmt.setInt(i++, status == 1 ? 1 : -1);         // status
        targetStmt.setString(i++, ip);                        // ip
        if (createTime == null) {
            targetStmt.setTimestamp(i++, Timestamp.valueOf(LocalDateTime.now())); // register_time
        } else {
            targetStmt.setTimestamp(i++, createTime);
        }
        // last_login_time 固定 NULL

        try {
            targetStmt.executeUpdate();
            migrated++;
        } catch (java.sql.SQLIntegrityConstraintViolationException e) {
            skippedConflict++;
            System.err.println("唯一键冲突，跳过：email=" + email + " user_name=" + userName);
        }
    }
}
