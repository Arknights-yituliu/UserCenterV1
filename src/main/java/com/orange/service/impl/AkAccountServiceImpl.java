package com.orange.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BusinessException;
import com.orange.common.exception.RateLimitedException;
import com.orange.common.util.LogUtil;
import com.orange.common.util.RedisKeyUtil;
import com.orange.common.util.SignUtil;
import com.orange.entity.dto.akoperator.ItemEntryRequest;
import com.orange.entity.dto.akoperator.OperatorItemRequest;
import com.orange.entity.dto.akoperator.OperatorSaveRequest;
import com.orange.entity.po.AkAccountBinding;
import com.orange.entity.po.AkDefaultSource;
import com.orange.entity.po.AkItemData;
import com.orange.entity.po.AkPlayerInfo;
import com.orange.entity.po.AkOperatorState;
import com.orange.entity.po.AkOperatorStateManualSummary;
import com.orange.entity.vo.akoperator.AkAccountVO;
import com.orange.entity.vo.akoperator.ItemListVO;
import com.orange.entity.vo.akoperator.ItemVO;
import com.orange.entity.vo.akoperator.OperatorListVO;
import com.orange.entity.vo.akoperator.OperatorSaveResultVO;
import com.orange.entity.vo.akoperator.OperatorVO;
import com.orange.mapper.AkAccountBindingMapper;
import com.orange.mapper.AkDefaultSourceMapper;
import com.orange.mapper.AkItemDataMapper;
import com.orange.mapper.AkPlayerInfoMapper;
import com.orange.mapper.AkOperatorStateManualMapper;
import com.orange.mapper.AkOperatorStateMapper;
import com.orange.service.AkAccountService;
import com.orange.service.AkOperatorStateManualService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 游戏账号与干员数据服务实现
 *
 * <p>鉴权与归属：uid 取自可信登录上下文；读取前必须核对本人的绑定关系，
 * 未绑定统一返回业务码 80008，不通过错误消息暴露账号是否存在。</p>
 *
 * <p>保存顺序：登录鉴权（拦截器）→ 目标账号与角色信息校验 → 归一化干员数据 →
 * 占用该 ak_uid 的上传限流名额 → 开启写事务，避免非法请求占用别人的名额。
 * 首次上传该 ak_uid 时不做额外凭据校验，绑定关系在写事务中直接建立。</p>
 *
 * @author UserCenter
 */
@Service
public class AkAccountServiceImpl implements AkAccountService {

    /** 游戏账号 UID 合法格式：不超过 32 位可见 ASCII 字符（与列定义 VARCHAR(32) utf8mb4_bin 一致，保持大小写敏感） */
    private static final Pattern AK_UID_PATTERN = Pattern.compile("^[\\x21-\\x7E]{1,32}$");

    /** 上传限流业务标识，构成 Redis key 的中间段 */
    private static final String RATE_LIMIT_BIZ = "ak-operator-save";

    /** 同一 ak_uid 上传限流窗口（秒）：窗口内最多接受 1 个保存请求，全部绑定该账号的 uid 共享名额 */
    private static final long SAVE_RATE_LIMIT_WINDOW_SECONDS = 2L;

    /** 唯一键冲突/死锁时整事务回滚后的最大重试次数（不含首次执行） */
    private static final int SAVE_MAX_RETRY = 2;

    /** 物品数据 JSON 文本大小上限（字节），防止单条数据过大占用存储 */
    private static final long MAX_ITEM_DATA_BYTES = 2L * 1024 * 1024;

    /** 账号列表排序：用户设置的默认来源在前，其余按最近变更时间倒序，时间缺失的排最后 */
    private static final Comparator<AkAccountVO> ACCOUNT_ORDER = (left, right) -> {
        if (left.isDefaultFlag() != right.isDefaultFlag()) {
            return left.isDefaultFlag() ? -1 : 1;
        }
        LocalDateTime leftTime = left.getUpdateTime();
        LocalDateTime rightTime = right.getUpdateTime();
        if (leftTime == null && rightTime == null) {
            return 0;
        }
        if (leftTime == null) {
            return 1;
        }
        if (rightTime == null) {
            return -1;
        }
        return rightTime.compareTo(leftTime);
    };

    private final AkAccountBindingMapper bindingMapper;
    private final AkDefaultSourceMapper defaultSourceMapper;
    private final AkPlayerInfoMapper playerInfoMapper;
    private final AkOperatorStateMapper operatorMapper;
    private final AkItemDataMapper itemDataMapper;
    private final AkOperatorStateManualMapper manualMapper;
    private final AkOperatorStateManualService manualOperatorService;
    private final AkOperatorSaveExecutor saveExecutor;
    private final AkAccountUnbindExecutor unbindExecutor;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 构造器注入依赖
     *
     * @param bindingMapper         绑定关系 Mapper
     * @param defaultSourceMapper   默认数据来源偏好 Mapper
     * @param playerInfoMapper      角色信息 Mapper
     * @param operatorMapper        干员数据 Mapper
     * @param itemDataMapper        物品存量 Mapper，读取整份 JSON 快照
     * @param manualMapper          手动版干员数据 Mapper（仅用于账号列表的汇总时间）
     * @param manualOperatorService 手动版干员数据服务，读取手动条目时复用其字段映射
     * @param saveExecutor          保存写事务执行器（独立 Bean 保证 @Transactional 生效）
     * @param unbindExecutor        解绑写事务执行器（独立 Bean 保证 @Transactional 生效）
     * @param stringRedisTemplate   Redis 客户端，用于跨实例的原子限流
     * @param objectMapper          JSON 序列化器，用于物品数据整体落库与读取解析
     */
    public AkAccountServiceImpl(AkAccountBindingMapper bindingMapper,
                                AkDefaultSourceMapper defaultSourceMapper,
                                AkPlayerInfoMapper playerInfoMapper,
                                AkOperatorStateMapper operatorMapper,
                                AkItemDataMapper itemDataMapper,
                                AkOperatorStateManualMapper manualMapper,
                                AkOperatorStateManualService manualOperatorService,
                                AkOperatorSaveExecutor saveExecutor,
                                AkAccountUnbindExecutor unbindExecutor,
                                StringRedisTemplate stringRedisTemplate,
                                ObjectMapper objectMapper) {
        this.bindingMapper = bindingMapper;
        this.defaultSourceMapper = defaultSourceMapper;
        this.playerInfoMapper = playerInfoMapper;
        this.operatorMapper = operatorMapper;
        this.itemDataMapper = itemDataMapper;
        this.manualMapper = manualMapper;
        this.manualOperatorService = manualOperatorService;
        this.saveExecutor = saveExecutor;
        this.unbindExecutor = unbindExecutor;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询当前用户可展示的数据来源列表，默认来源在前，其余按最近一次变更时间倒序
     *
     * <p>列表由森空岛自动导入的绑定账号与手动上传条目合并而成。用户设置的默认来源
     * （见 {@code ak_default_source}）标记为 {@code defaultFlag} 并排在最前；未设置或
     * 默认来源已失效时不做标记，整体回退为按 {@code updateTime} 倒序。手动上传条目没有
     * 游戏账号，akUid 复用用户中心 UID，且仅在用户存在手动数据时追加。</p>
     *
     * @param uid 用户中心 UID
     * @return 数据来源列表（含来源、默认标记、创建时间与最近变更时间），默认项在前，无数据时返回空列表
     */
    @Override
    public List<AkAccountVO> listBoundAccounts(Long uid) {
        AkDefaultSource defaultSource = defaultSourceMapper.selectByUid(uid);
        List<AkAccountBinding> bindings = bindingMapper.selectByOwnerOrderByUpdateTime(uid);
        List<AkAccountVO> accounts = new ArrayList<>(bindings.size() + 1);
        if (!bindings.isEmpty()) {
            List<String> akUids = new ArrayList<>(bindings.size());
            for (AkAccountBinding binding : bindings) {
                akUids.add(binding.getAkUid());
            }
            Map<String, AkPlayerInfo> infoMap = loadPlayerInfoMap(akUids);
            for (AkAccountBinding binding : bindings) {
                // 角色信息缺失时不展示，但保留绑定关系本身
                if (!infoMap.containsKey(binding.getAkUid())) {
                    continue;
                }
                AkAccountVO account = toAccountVO(binding);
                account.setDefaultFlag(matchesDefault(defaultSource, account.getSource(), account.getAkUid()));
                accounts.add(account);
            }
        }
        AkOperatorStateManualSummary manualSummary = manualMapper.selectSummaryByUid(uid);
        if (manualSummary != null && manualSummary.getUpdateTime() != null) {
            AkAccountVO account = toManualAccountVO(uid, manualSummary);
            account.setDefaultFlag(matchesDefault(defaultSource, account.getSource(), account.getAkUid()));
            accounts.add(account);
        }
        accounts.sort(ACCOUNT_ORDER);
        return accounts;
    }

    /**
     * 全量读取干员数据，手动条目与自动数据分别走不同数据表
     *
     * <p>归属：手动数据只读当前登录用户本人那一份，因此 {@code source=manual} 时还要求
     * {@code akUid} 等于当前用户中心 UID，避免客户端拿别人的 akUid 取回本人手动数据；
     * {@code source} 为空时按 akUid 自动识别，本人不存在手动记录才回落到绑定关系鉴权的自动数据。
     * 未传 {@code akUid} 时按用户设置的默认来源读取，未设置或默认来源失效则取最近变更的条目。</p>
     *
     * <p>返回口径：只返回已持有（owned=1）的干员，自动导入与手动版一致；owned=0 的记录
     * 仍保留在库中，但不出现在读取结果里。</p>
     *
     * @param uid    用户中心 UID
     * @param akUid  数据条目 UID：自动数据为游戏账号 UID，手动数据复用用户中心 UID，可为空
     * @param source 数据来源，可选：manual 手动版，skland 森空岛自动导入，为空时自动识别
     * @return 对应来源的全部已持有干员数据，无数据时 items 为空数组
     */
    @Override
    public OperatorListVO listOperators(Long uid, String akUid, String source) {
        if (akUid == null || akUid.isEmpty()) {
            return listDefaultOperators(uid, source);
        }
        validateAkUid(akUid);
        // 手动条目没有游戏账号，akUid 复用用户中心 UID
        String manualAkUid = String.valueOf(uid);
        if (StringUtils.hasText(source)) {
            if (AkAccountVO.SOURCE_MANUAL.equals(source)) {
                if (!manualAkUid.equals(akUid)) {
                    throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "手动数据来源的 akUid 必须为当前用户中心UID");
                }
                return new OperatorListVO(akUid, manualOperatorService.listManualOperators(uid));
            }
            if (!AkAccountVO.SOURCE_SKLAND.equals(source)) {
                throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "source 仅支持 manual 或 skland");
            }
        } else if (manualAkUid.equals(akUid)) {
            // 未显式指定来源时按 akUid 识别手动条目：只要本人存在手动记录就视为手动来源，
            // 即使记录全部为 owned=0 也返回空列表，不回落自动数据；不存在手动记录时才回落到
            // 自动数据，兼容游戏账号 akUid 恰好与用户中心 UID 撞号的情况
            AkOperatorStateManualSummary manualSummary = manualMapper.selectSummaryByUid(uid);
            if (manualSummary != null && manualSummary.getUpdateTime() != null) {
                return new OperatorListVO(akUid, manualOperatorService.listManualOperators(uid));
            }
        }

        requireBinding(akUid, uid);
        List<AkOperatorState> rows = operatorMapper.selectByAkUid(akUid);
        List<OperatorVO> items = new ArrayList<>(rows.size());
        for (AkOperatorState row : rows) {
            if (!Integer.valueOf(1).equals(row.getOwned())) {
                continue;
            }
            items.add(toOperatorVO(row));
        }
        return new OperatorListVO(akUid, items);
    }

    /**
     * 未指定 akUid 时按默认来源读取：默认项优先，未设置或默认项已失效时取最近变更的条目
     *
     * @param uid    用户中心 UID
     * @param source 数据来源，必须为空（默认来源由服务端决定，不接受客户端同时指定）
     * @return 默认条目的全部干员数据
     */
    private OperatorListVO listDefaultOperators(Long uid, String source) {
        if (StringUtils.hasText(source)) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "未指定 akUid 时不能指定 source");
        }
        List<AkAccountVO> accounts = listBoundAccounts(uid);
        if (accounts.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "当前用户暂无可用的明日方舟数据来源");
        }
        // listBoundAccounts 已保证默认来源排首位、其余按最近变更倒序，首条即本次要读取的条目
        AkAccountVO account = accounts.get(0);
        return listOperators(uid, account.getAkUid(), account.getSource());
    }

    /**
     * 设置当前用户的默认数据来源
     *
     * <p>先校验目标来源归属再落库：manual 只允许指向本人且已有手动数据，skland 只允许
     * 指向本人已绑定的游戏账号；偏好按用户唯一，重复设置直接覆盖，不保留历史。</p>
     *
     * @param uid    用户中心 UID
     * @param akUid  数据条目 UID
     * @param source 数据来源：manual 或 skland
     */
    @Override
    public void setDefaultSource(Long uid, String akUid, String source) {
        validateAkUid(akUid);
        if (AkAccountVO.SOURCE_MANUAL.equals(source)) {
            requireManualSource(uid, akUid);
        } else if (AkAccountVO.SOURCE_SKLAND.equals(source)) {
            requireBinding(akUid, uid);
        } else {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "source 仅支持 manual 或 skland");
        }
        defaultSourceMapper.upsert(uid, source, akUid);
    }

    /**
     * 解除当前用户与森空岛游戏账号的绑定：只删绑定与默认偏好，共享数据保留
     *
     * <p>{@code ak_player_info / ak_operator_state / ak_item_data} 按 ak_uid 由全部
     * 绑定用户共享，解绑不触碰这些表，其他用户与后续重新导入不受影响。</p>
     *
     * @param uid   用户中心 UID
     * @param akUid 游戏账号 UID
     */
    @Override
    public void unbindAccount(Long uid, String akUid) {
        validateAkUid(akUid);
        // 未绑定与账号不存在都返回同一权限码，不暴露账号是否存在
        requireBinding(akUid, uid);
        unbindExecutor.unbind(uid, akUid);
    }

    /**
     * 校验手动来源的目标：akUid 必须是本人用户中心 UID，且本人已存在手动修改版数据
     *
     * @param uid   用户中心 UID
     * @param akUid 数据条目 UID
     */
    private void requireManualSource(Long uid, String akUid) {
        if (!String.valueOf(uid).equals(akUid)) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "手动数据来源的 akUid 必须为当前用户中心UID");
        }
        AkOperatorStateManualSummary summary = manualMapper.selectSummaryByUid(uid);
        if (summary == null || summary.getUpdateTime() == null) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "手动修改版干员数据不存在");
        }
    }

    /**
     * 判断账号条目是否为用户设置的默认来源
     *
     * @param defaultSource 用户默认来源偏好，可能为 null（未设置）
     * @param source        条目数据来源
     * @param akUid         条目归属键
     * @return 是否命中默认来源
     */
    private boolean matchesDefault(AkDefaultSource defaultSource, String source, String akUid) {
        return defaultSource != null
                && source.equals(defaultSource.getSource())
                && akUid.equals(defaultSource.getAkUid());
    }

    /**
     * 全量读取游戏账号物品存量：校验绑定归属后返回该账号最近一次上报的完整物品快照
     *
     * <p>物品是自动导入侧的全量快照，没有手动/自动来源之分；
     * 该账号从未携带 items 上传时返回空数组与 null 时间。</p>
     *
     * @param uid   用户中心 UID
     * @param akUid 游戏账号 UID
     * @return 物品全量数据与最近上传时间
     */
    @Override
    public ItemListVO listItems(Long uid, String akUid) {
        validateAkUid(akUid);
        requireBinding(akUid, uid);
        AkItemData data = itemDataMapper.selectByAkUid(akUid);
        if (data == null) {
            return new ItemListVO(akUid, new ArrayList<>(), null);
        }
        return new ItemListVO(akUid, parseItems(data), data.getUpdateTime());
    }

    /**
     * 批量保存某游戏账号的角色信息、干员数据与物品存量
     *
     * @param uid     用户中心 UID
     * @param request 保存参数（角色信息 + 干员数组 + 可选物品数组），目标游戏账号取自 request.playerInfo.akUid
     * @return 新增/更新/未变更条数统计，三项之和等于传入干员记录数
     */
    @Override
    public OperatorSaveResultVO saveOperators(Long uid, OperatorSaveRequest request) {
        String akUid = request.getPlayerInfo().getAkUid();
        validateAkUid(akUid);
        // 先归一化（缺省/null/空字符串补 0）再校验重复 ID，保证比较语义与落库值一致
        List<AkOperatorState> operators = normalizeOperators(akUid, request.getOperators());
        String itemData = serializeItems(request.getItems());

        // 参数校验通过后占用该 ak_uid 的限流名额；首次上传不做凭据校验，绑定关系由写事务建立
        acquireSaveQuota(akUid);

        return saveWithRetry(akUid, uid, operators, itemData);
    }

    /**
     * 调用写事务执行器保存，遇唯一键冲突或死锁时整事务回滚后重试整个请求
     *
     * <p>限流名额只在首次执行前占用一次，内部重试不重复占用。</p>
     *
     * @param akUid     游戏账号 UID
     * @param uid       用户中心 UID
     * @param operators 本次提交的干员记录（已归一化）
     * @param itemData  本次上报的物品 JSON 文本，null 表示不上报物品
     * @return 新增/更新/未变更条数统计
     */
    private OperatorSaveResultVO saveWithRetry(String akUid,
                                               Long uid,
                                               List<AkOperatorState> operators,
                                               String itemData) {
        for (int attempt = 0; ; attempt++) {
            try {
                return saveExecutor.save(akUid, uid, operators, itemData);
            } catch (DuplicateKeyException | PessimisticLockingFailureException e) {
                if (attempt >= SAVE_MAX_RETRY) {
                    LogUtil.warn(AkAccountServiceImpl.class, "干员数据保存冲突重试耗尽, akUidHash={}, attempts={}",
                            SignUtil.sha256(akUid), attempt + 1, e);
                    throw new BusinessException(ResultCode.OPERATOR_SAVE_CONFLICT);
                }
                LogUtil.info(AkAccountServiceImpl.class, "干员数据保存冲突，回滚后重试整个请求, akUidHash={}, attempt={}",
                        SignUtil.sha256(akUid), attempt + 1);
            }
        }
    }

    /**
     * 占用同一 ak_uid 的上传限流名额：Redis SET NX EX 原子操作，多实例共享
     *
     * <p>名额已被占用返回业务码 30006 并携带等待秒数，不读写角色/干员表也不排队；
     * 限流设施不可用返回业务码 40003，不默默绕过限流。</p>
     *
     * @param akUid 游戏账号 UID
     */
    private void acquireSaveQuota(String akUid) {
        String key = RedisKeyUtil.rate(RATE_LIMIT_BIZ, SignUtil.sha256(akUid));
        boolean acquired;
        try {
            acquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue()
                    .setIfAbsent(key, "1", Duration.ofSeconds(SAVE_RATE_LIMIT_WINDOW_SECONDS)));
        } catch (Exception e) {
            LogUtil.error(AkAccountServiceImpl.class, "干员上传限流设施不可用, key={}", key, e);
            throw new BusinessException(ResultCode.RATE_LIMITER_UNAVAILABLE);
        }
        if (!acquired) {
            throw new RateLimitedException(ResultCode.AK_UPLOAD_RATE_LIMITED, SAVE_RATE_LIMIT_WINDOW_SECONDS);
        }
    }

    /**
     * 核对本人绑定关系，未绑定返回业务码 80008
     *
     * <p>查询他人账号与查询不存在的绑定关系返回同一权限码，不暴露账号是否存在。</p>
     *
     * @param akUid    游戏账号 UID
     * @param uid      用户中心 UID
     */
    private void requireBinding(String akUid, Long uid) {
        if (bindingMapper.countBinding(akUid, uid) == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /**
     * 归一化干员数组：数值字段缺省/null 统一补 0（owned 缺省补 1），并拒绝同一请求中的重复干员 ID
     *
     * @param akUid 游戏账号 UID
     * @param items 客户端提交的干员数组（已通过 Bean Validation 的格式与范围校验）
     * @return 可直接落库的干员记录列表
     */
    private List<AkOperatorState> normalizeOperators(String akUid, List<OperatorItemRequest> items) {
        Set<String> seenIds = new HashSet<>(Math.max(16, items.size() * 2));
        List<AkOperatorState> result = new ArrayList<>(items.size());
        for (OperatorItemRequest item : items) {
            if (!seenIds.add(item.getId())) {
                throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "同一请求中干员ID重复：" + item.getId());
            }
            result.add(toStateRecord(akUid, item));
        }
        return result;
    }

    /**
     * 将单条干员请求转换为落库实体，数值字段 null 归一化为 0（owned 归一化为 1）
     *
     * @param akUid 游戏账号 UID
     * @param item  客户端提交的干员记录
     * @return 干员落库实体（id 与 updatedAt 由服务端维护）
     */
    private AkOperatorState toStateRecord(String akUid, OperatorItemRequest item) {
        AkOperatorState record = new AkOperatorState();
        record.setAkUid(akUid);
        record.setOperatorId(item.getId());
        record.setOwned(ownedIfNull(item.getOwned()));
        record.setRarity(zeroIfNull(item.getRarity()));
        record.setLevel(zeroIfNull(item.getLevel()));
        record.setEvolvePhase(zeroIfNull(item.getEvolvePhase()));
        record.setMainSkillLevel(zeroIfNull(item.getMainSkillLevel()));
        record.setSkill1(zeroIfNull(item.getSkill1()));
        record.setSkill2(zeroIfNull(item.getSkill2()));
        record.setSkill3(zeroIfNull(item.getSkill3()));
        record.setEquipX(zeroIfNull(item.getEquipX()));
        record.setEquipY(zeroIfNull(item.getEquipY()));
        record.setEquipD(zeroIfNull(item.getEquipD()));
        record.setEquipA(zeroIfNull(item.getEquipA()));
        record.setEquipB(zeroIfNull(item.getEquipB()));
        record.setPotentialRank(zeroIfNull(item.getPotentialRank()));
        return record;
    }

    /**
     * 校验并序列化物品数组：拒绝重复物品编码，序列化后的 JSON 文本不超过大小上限
     *
     * <p>物品数据按账号整份保存：客户端数组整体序列化为一个 JSON 文本，
     * 每次携带 items 时覆盖该账号已有的物品数据。</p>
     *
     * @param items 客户端提交的物品数组，可为空表示本次不上报物品
     * @return 可直接落库的 JSON 文本，未上报时返回 null
     */
    private String serializeItems(List<ItemEntryRequest> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        Set<String> seenIds = new HashSet<>(Math.max(16, items.size() * 2));
        for (ItemEntryRequest item : items) {
            if (!seenIds.add(item.getItemId())) {
                throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "同一请求中物品ID重复：" + item.getItemId());
            }
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(items);
        } catch (JsonProcessingException e) {
            LogUtil.error(AkAccountServiceImpl.class, "物品数据序列化失败", e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "物品数据序列化失败");
        }
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_ITEM_DATA_BYTES) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "物品数据不能超过2MB");
        }
        return json;
    }

    /**
     * 把落库的物品 JSON 文本解析为读取结果列表
     *
     * <p>写入时已保证文本是合法的 items 数组，此处解析失败只可能是数据被人工改动，
     * 按系统错误返回而不是静默返回空数据。</p>
     *
     * @param data 物品数据行
     * @return 解析后的物品列表
     */
    private List<ItemVO> parseItems(AkItemData data) {
        try {
            return objectMapper.readValue(data.getItemData(), new TypeReference<List<ItemVO>>() {
            });
        } catch (JsonProcessingException e) {
            LogUtil.error(AkAccountServiceImpl.class, "物品数据解析失败, akUidHash={}",
                    SignUtil.sha256(data.getAkUid()), e);
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "物品数据解析失败");
        }
    }

    /**
     * 批量读取角色信息并按 ak_uid 建立映射
     *
     * @param akUids 游戏账号 UID 列表，调用方保证非空
     * @return ak_uid 到角色信息的映射
     */
    private Map<String, AkPlayerInfo> loadPlayerInfoMap(List<String> akUids) {
        List<AkPlayerInfo> infos = playerInfoMapper.selectByAkUids(akUids);
        Map<String, AkPlayerInfo> infoMap = new HashMap<>(Math.max(16, infos.size() * 2));
        for (AkPlayerInfo info : infos) {
            infoMap.put(info.getAkUid(), info);
        }
        return infoMap;
    }

    /**
     * 绑定关系实体转已绑定账号视图对象
     *
     * <p>账号 UID 取自绑定表，创建时间与最近导入时间一并返回，供前端排序与展示。</p>
     *
     * @param binding 绑定关系实体
     * @return 账号视图对象
     */
    private AkAccountVO toAccountVO(AkAccountBinding binding) {
        AkAccountVO vo = new AkAccountVO();
        vo.setAkUid(binding.getAkUid());
        vo.setSource(AkAccountVO.SOURCE_SKLAND);
        vo.setCreateTime(binding.getCreateTime());
        vo.setUpdateTime(binding.getUpdateTime());
        return vo;
    }

    /**
     * 手动版数据汇总转手动上传条目
     *
     * <p>手动数据按用户归属、没有游戏账号，akUid 复用用户中心 UID；手动版没有创建时间，
     * createTime 留空，updateTime 取该用户手动记录的 MAX(updated_at)。</p>
     *
     * @param uid     用户中心 UID
     * @param summary 手动版数据时间汇总
     * @return 账号视图对象，source 为 manual
     */
    private AkAccountVO toManualAccountVO(Long uid, AkOperatorStateManualSummary summary) {
        AkAccountVO vo = new AkAccountVO();
        vo.setAkUid(String.valueOf(uid));
        vo.setSource(AkAccountVO.SOURCE_MANUAL);
        vo.setUpdateTime(summary.getUpdateTime());
        return vo;
    }

    /**
     * 干员实体转视图对象：数据库行 ID 命名为 recordId，数值字段始终返回数字
     *
     * @param row 干员实体
     * @return 干员视图对象
     */
    private OperatorVO toOperatorVO(AkOperatorState row) {
        OperatorVO vo = new OperatorVO();
        vo.setId(row.getOperatorId());
        vo.setRecordId(row.getId());
        vo.setOwned(zeroIfNull(row.getOwned()));
        vo.setRarity(zeroIfNull(row.getRarity()));
        vo.setLevel(zeroIfNull(row.getLevel()));
        vo.setEvolvePhase(zeroIfNull(row.getEvolvePhase()));
        vo.setMainSkillLevel(zeroIfNull(row.getMainSkillLevel()));
        vo.setSkill1(zeroIfNull(row.getSkill1()));
        vo.setSkill2(zeroIfNull(row.getSkill2()));
        vo.setSkill3(zeroIfNull(row.getSkill3()));
        vo.setEquipX(zeroIfNull(row.getEquipX()));
        vo.setEquipY(zeroIfNull(row.getEquipY()));
        vo.setEquipD(zeroIfNull(row.getEquipD()));
        vo.setEquipA(zeroIfNull(row.getEquipA()));
        vo.setEquipB(zeroIfNull(row.getEquipB()));
        vo.setPotentialRank(zeroIfNull(row.getPotentialRank()));
        return vo;
    }

    /**
     * 校验游戏账号 UID 格式
     *
     * @param akUid 游戏账号 UID
     */
    private void validateAkUid(String akUid) {
        if (!StringUtils.hasText(akUid) || !AK_UID_PATTERN.matcher(akUid).matches()) {
            throw new BusinessException(ResultCode.PARAM_VALID_ERROR, "游戏账号UID必须为不超过32位的可见ASCII字符");
        }
    }

    /**
     * 数值归一化：null 视为 0（与显式传 0 等价，不表示沿用旧值）
     *
     * @param value 原始值
     * @return 非空整数值
     */
    private Integer zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }

    /**
     * 是否持有归一化：null 视为已持有（自动导入缺省按 1，与历史数据语义一致）
     *
     * @param value 原始值
     * @return 0 或 1
     */
    private Integer ownedIfNull(Integer value) {
        return value == null ? 1 : value;
    }

}
