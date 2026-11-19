package com.orange.common.util;

import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.entity.dto.pat.PatPayload;

/**
 * PAT payload 取值工具
 *
 * <p>payload 是结构固定的 {@link PatPayload}。{@code game_uid} 用于定位明日方舟账号或
 * 终末地角色，终末地还使用 {@code server_id} 定位服务器，因此 game_uid 的取值口径必须收得
 * 很紧——缺失、空白一律报 10002，绝不返回空串。</p>
 *
 * <p>为什么不能放行空值：业务层把「akUid 为空」解释为「读用户设置的默认来源」，
 * 一旦空串被传下去，请求会静默读到另一个账号的数据，属于越权。账号归属最终由
 * {@code ak_account_binding} / {@code ef_account_binding} 的绑定校验把关。</p>
 *
 * <p>{@code game_uid} 在实体里是 {@code String}，JSON 中的整型数字已由 Jackson 转成十进制
 * 字符串，这里只需 trim 与判空，不再做 {@code Object} 的类型分支。</p>
 *
 * @author UserCenter
 */
public final class PatPayloadUtil {

    /** 游戏数据接口约定的 payload 字段：要读取的游戏账号或角色 UID */
    public static final String KEY_GAME_UID = PatPayload.FIELD_GAME_UID;

    private PatPayloadUtil() {
    }

    /**
     * 从 PAT payload 中取出 {@code game_uid}
     *
     * <p>接受字符串，以及由 JSON 整型数字转换而来的十进制字符串；去空白后必须非空。</p>
     *
     * @param payload PAT 的 payload，可为 null
     * @return 去空白后的 game_uid，非空
     * @throws BusinessException payload 未配置或取值为空白（10002）
     */
    public static String requireGameUid(PatPayload payload) {
        String value = payload == null ? null : payload.getGameUid();
        if (value == null) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "PAT 未配置 game_uid");
        }
        String text = value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "PAT 的 game_uid 不能为空");
        }
        return text;
    }
}
