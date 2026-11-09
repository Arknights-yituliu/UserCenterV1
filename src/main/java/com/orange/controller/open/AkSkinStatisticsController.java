package com.orange.controller.open;

import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.Result;
import com.orange.common.util.SignUtil;
import com.orange.entity.vo.akoperator.SkinStatisticsVO;
import com.orange.service.AkSkinStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 皮肤持有数据统计查询接口，路径 {@code /open/ak-skin-statistics/result}
 *
 * <p>路径在 {@code /open} 下，不经过登录拦截器，但需要携带访问密钥：调用时在请求参数
 * {@value #KEY_PARAM} 中传明文密钥，服务端只保存密钥的 SHA-256 摘要，校验时对请求明文
 * 做同样摘要后比对（与后台控制台 RecycleDropController 的校验方式一致）。</p>
 *
 * <p>只读结果表 ak_skin_statistics，不触发全表扫描，接口耗时与源表数据量无关。</p>
 *
 * <p>统计结果由 {@link com.orange.service.scheduler.AkSkinStatisticsScheduler} 定时异步刷新，
 * 每小时 10 分一轮；跑一轮要扫描 ak_skin_state 全表，属离线跑批，因此不提供手动触发入口，
 * 避免反复触发把数据库压满。</p>
 *
 * @author UserCenter
 */
@Tag(name = "皮肤数据统计")
@RestController
@RequestMapping("/open")
public class AkSkinStatisticsController {

    /** 密钥参数名 */
    private static final String KEY_PARAM = "key";

    /**
     * 密钥明文的 SHA-256 摘要（小写十六进制），此处只保存摘要，不保存明文
     *
     * <p>与后台控制台（BackEndV3 RecycleDropController）使用同一把密钥，便于控制台直接复用。
     * 换密钥时用 {@code printf %s '新密钥' | sha256sum} 生成摘要后替换此常量即可。</p>
     */
    private static final String API_KEY_SHA256 = "91e49dd07365b175fecd09017bdbba19c978707f4d5d5ccd628dc799a72bef70";

    private final AkSkinStatisticsService statisticsService;

    /**
     * 构造器注入统计服务
     *
     * @param statisticsService 皮肤数据统计服务
     */
    public AkSkinStatisticsController(AkSkinStatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 查询最近一轮皮肤统计结果
     *
     * @param key 访问密钥明文
     * @return 统一响应，data 为按皮肤编码升序排列的统计结果；尚未跑过统计时 data 为空列表
     * @throws BusinessException 密钥缺失或不匹配时抛出，返回无权限操作
     */
    @Operation(summary = "查询皮肤持有统计结果（需携带 key 参数）")
    @GetMapping("/ak-skin-statistics/result")
    public Result<List<SkinStatisticsVO>> list(
            @RequestParam(value = KEY_PARAM, required = false) String key) {
        validateApiKey(key);
        return Result.success(statisticsService.getSkinStatistics());
    }

    /**
     * 校验请求参数中的密钥：对明文做 SHA-256 后与硬编码的摘要比对
     *
     * <p>用 {@link MessageDigest#isEqual} 做常量时间比较，避免按字节比较时的时序侧信道。
     * 摘要按 UUID 风格的小写十六进制存放，比较的是摘要字符串的字节，不是原始摘要字节。</p>
     *
     * @param key 请求携带的密钥明文
     * @throws BusinessException 密钥缺失或不匹配时抛出，返回无权限操作
     */
    private void validateApiKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        byte[] actual = SignUtil.sha256(key.trim()).getBytes(StandardCharsets.UTF_8);
        byte[] expected = API_KEY_SHA256.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(actual, expected)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }
}
