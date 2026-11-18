package com.orange.common.util;

import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;

import java.math.BigInteger;
import java.util.Map;

/**
 * PAT payload 取值工具
 *
 * <p>payload 是调用方自定义的 KV，服务端默认不解读。当前唯一被解读的键是
 * {@link #KEY_AK_UID}：PAT 干员数据接口据此定位要读取的游戏账号，因此该键的取值口径
 * 必须收得很紧——缺失、空白、类型不符一律报 10002，绝不返回空串。</p>
 *
 * <p>为什么不能放行空值：业务层把「akUid 为空」解释为「读用户设置的默认来源」，
 * 一旦空串被传下去，请求会静默读到另一个账号的数据，属于越权。</p>
 *
 * @author UserCenter
 */
public final class PatPayloadUtil {

    /** 干员数据接口约定的 payload 键：要读取的游戏账号 UID */
    public static final String KEY_AK_UID = "ak_uid";

    private PatPayloadUtil() {
    }

    /**
     * 从 PAT payload 中取出 {@code ak_uid}
     *
     * <p>接受字符串与整型数字两种写法（调用方用 JSON 写 payload 时容易把 UID 写成数字），
     * 其它类型一律拒绝，避免 {@code true}、对象、浮点数被转成无意义的账号串。</p>
     *
     * @param payload PAT 的 payload，可为 null
     * @return 去空白后的 ak_uid，非空
     * @throws BusinessException payload 未配置或取值不合法（10002）
     */
    public static String requireAkUid(Map<String, Object> payload) {
        Object value = payload == null ? null : payload.get(KEY_AK_UID);
        if (value == null) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "PAT 未配置 ak_uid");
        }
        String text;
        if (value instanceof String str) {
            text = str.trim();
        } else if (value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof BigInteger) {
            text = value.toString();
        } else {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "PAT 的 ak_uid 必须为字符串或整数");
        }
        if (text.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "PAT 的 ak_uid 不能为空");
        }
        return text;
    }
}
