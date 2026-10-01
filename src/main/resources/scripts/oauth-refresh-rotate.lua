-- OAuth refresh_token 轮转脚本（客户端开启 rotate_refresh_token 时使用）
--
-- 模型：旧 refresh_token（R1）在本次刷新后立即失效，由服务端换发新 refresh_token（R2）。
--   - 墓碑（refresh:used:{R1}）长期保留至 R1 原到期时间，用于识别「旧凭证被重放」；
--   - 宽限期幂等副本（refresh:next:{R1}）只在宽限期内保留，让响应丢失后的重试与
--     并发刷新落败方拿到同一份新凭证，避免被误判为凭证泄露；
--   - 家族索引（family:{familyId}）登记同族 access/refresh，重放确认后据此整族吊销。
--
-- KEYS:
--   KEYS[1] = 旧 refresh 记录 key（uc:oauth:refresh:{R1}）
--   KEYS[2] = 新 access 记录 key（uc:oauth:access:{newAccess}）
--   KEYS[3] = 新 refresh 记录 key（uc:oauth:refresh:{R2}）
--   KEYS[4] = uid -> OAuth token 反向索引 Set（uc:uid:oauth:{uid}）
--   KEYS[5] = 轮转墓碑 key（uc:oauth:refresh:used:{R1}）
--   KEYS[6] = 宽限期幂等副本 key（uc:oauth:refresh:next:{R1}）
--   KEYS[7] = 令牌家族索引 Set（uc:oauth:family:{familyId}）
-- ARGV:
--   ARGV[1]  = Java 已校验过的 R1 原始 JSON（快照，用于并发比对）
--   ARGV[2]  = 新 access 记录 JSON
--   ARGV[3]  = 新 access TTL（秒）
--   ARGV[4]  = 新 refresh 记录 JSON
--   ARGV[5]  = 新 access 在 uid 反向索引中的成员（access:{newAccess}）
--   ARGV[6]  = 新 refresh 在 uid 反向索引中的成员（refresh:{R2}）
--   ARGV[7]  = 旧 refresh 在 uid 反向索引中的成员（refresh:{R1}）
--   ARGV[8]  = 墓碑 JSON（familyId / clientId / uid / rotatedAt）
--   ARGV[9]  = 宽限期秒数（幂等副本 TTL）
--   ARGV[10] = 新 refresh_token 明文（宽限期内重放时原样回传）
--   ARGV[11] = refresh TTL 兜底值（秒）：旧记录意外无 TTL 时使用
--
-- 返回:
--   1  = 轮转成功（R1 已失效，R2 已生效）
--   0  = R1 与快照不一致（已被轮转/被吊销/不存在），本次未做任何写入
--   -1 = 预校验失败，本次未做任何写入
--
-- Redis 原子执行整个脚本，“校验 R1 仍未变动 + 撤旧 + 签新 + 写墓碑”不可分割，
-- 因此并发刷新同一个 R1 只会有一个赢家。key 名在脚本内由 KEYS 直接给出，以单实例 Redis 为前提。

-- 将 KEYS/ARGV 绑定为可读局部变量（仅别名，不影响脚本执行）
local old_refresh_key = KEYS[1]    -- 旧 refresh_token 记录 key（待校验仍有效）
local access_key = KEYS[2]         -- 新 access_token 记录 key
local refresh_key = KEYS[3]        -- 新 refresh_token 记录 key
local uid_index_key = KEYS[4]      -- 该用户 OAuth 令牌反向索引 Set
local used_key = KEYS[5]           -- 轮转墓碑 key
local next_key = KEYS[6]           -- 宽限期幂等副本 key
local family_key = KEYS[7]         -- 令牌家族索引 Set

local expected_json = ARGV[1]      -- Java 验证过的旧 refresh 内容快照
local access_json = ARGV[2]        -- 新 access 记录 JSON
local access_ttl_arg = ARGV[3]     -- 新 access TTL（秒）
local refresh_json = ARGV[4]       -- 新 refresh 记录 JSON
local access_member = ARGV[5]      -- 新 access 索引成员
local refresh_member = ARGV[6]     -- 新 refresh 索引成员
local old_refresh_member = ARGV[7] -- 旧 refresh 索引成员
local tombstone_json = ARGV[8]     -- 墓碑 JSON
local grace_ttl_arg = ARGV[9]      -- 宽限期秒数
local next_refresh_token = ARGV[10] -- 新 refresh_token 明文
local fallback_ttl_arg = ARGV[11]  -- refresh TTL 兜底值（秒）

-- 第一步：校验 R1 仍原封未动（已轮转/已吊销/已过期则放弃）
local current = redis.call('GET', old_refresh_key)
if not current or current ~= expected_json then
    return 0
end

-- 第二步：预校验。Lua 原子执行但不回滚已运行的命令，因此写入前先检查
-- 所有可能导致后续命令失败的条件。
local access_ttl = tonumber(access_ttl_arg)
local grace_ttl = tonumber(grace_ttl_arg)
local fallback_ttl = tonumber(fallback_ttl_arg)
if not access_ttl or access_ttl <= 0
        or not grace_ttl or grace_ttl <= 0
        or not fallback_ttl or fallback_ttl <= 0 then
    return -1
end
local index_type = redis.call('TYPE', uid_index_key).ok
if index_type ~= 'none' and index_type ~= 'set' then
    return -1
end
local family_type = redis.call('TYPE', family_key).ok
if family_type ~= 'none' and family_type ~= 'set' then
    return -1
end
if redis.call('EXISTS', access_key) ~= 0 or redis.call('EXISTS', refresh_key) ~= 0 then
    return -1
end

-- 第三步：取旧 refresh 剩余有效期。轮转不延长授权，新 refresh 继承同一绝对到期时间，
-- 因此这里取的是旧记录的剩余 TTL 而不是客户的配置值。
local remaining_ttl = redis.call('TTL', old_refresh_key)
if remaining_ttl <= 0 then
    remaining_ttl = fallback_ttl
end

-- 第四步：写墓碑与宽限期幂等副本
redis.call('SET', used_key, tombstone_json, 'EX', remaining_ttl)
redis.call('SET', next_key, next_refresh_token, 'EX', grace_ttl)

-- 第五步：撤旧 + 签新
redis.call('DEL', old_refresh_key)
redis.call('SET', refresh_key, refresh_json, 'EX', remaining_ttl)
redis.call('SET', access_key, access_json, 'EX', access_ttl)

-- 第六步：维护 uid 反向索引与令牌家族索引
redis.call('SADD', uid_index_key, refresh_member)
redis.call('SADD', uid_index_key, access_member)
redis.call('SREM', uid_index_key, old_refresh_member)
redis.call('SADD', family_key, refresh_member)
redis.call('SADD', family_key, access_member)
-- 家族索引 TTL 只延长不缩短：仅在尚无过期时间（-1）或短于本次剩余期时重设，
-- 避免轮转把签发时为整族预留的有效期截短，导致成员未过期而家族先失效。
local family_ttl = redis.call('TTL', family_key)
if family_ttl < 0 or family_ttl < remaining_ttl then
    redis.call('EXPIRE', family_key, remaining_ttl)
end

return 1
