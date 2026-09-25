-- OAuth 迁移兑换签发脚本（按需兑换 / 懒迁移）
--
-- 模型：一个 (uid, client_id) 上恒只保留一条“迁移专用”凭证。
--   - 首次兑换：直接签发新 access/refresh 令牌对，并记下“当前迁移凭证”映射
--   - 重复兑换：先撤销上一轮迁移签发的 refresh_token（删除记录 + 摘除反向索引），
--               再签发新令牌对并更新映射
--   - 只影响迁移专用的那一条：映射不存在时不触碰该用户通过 /oauth2/direct-user
--     正常登录产生的授权记录
--
-- KEYS:
--   KEYS[1] = 当前迁移凭证映射 key（uc:oauth:migrate:current:{clientId}:{uid}）
--   KEYS[2] = 新 access_token 记录 key（uc:oauth:access:{newAccessToken}）
--   KEYS[3] = 新 refresh_token 记录 key（uc:oauth:refresh:{newRefreshToken}）
--   KEYS[4] = uid -> OAuth token 反向索引 Set（uc:uid:oauth:{uid}）
-- ARGV:
--   ARGV[1]  = 新 access 记录 JSON
--   ARGV[2]  = 新 refresh 记录 JSON
--   ARGV[3]  = 新 access TTL（秒）
--   ARGV[4]  = 新 refresh TTL（秒）
--   ARGV[5]  = 新 access 在反向索引中的成员（access:{newAccessToken}）
--   ARGV[6]  = 新 refresh 在反向索引中的成员（refresh:{newRefreshToken}）
--   ARGV[7]  = refresh 记录 key 前缀（uc:oauth:refresh:），用于定位待撤销的旧记录
--   ARGV[8]  = refresh 反向索引成员前缀（refresh:）
--   ARGV[9]  = 新 refresh_token 明文（写入映射，供下一轮替换时撤销）
--   ARGV[10] = 映射 key TTL（秒，与 refresh TTL 一致）
--
-- 返回（两元素数组）:
--   {1, ''}          = 成功，本次为首次兑换（无旧记录需要撤销）
--   {1, old_refresh} = 成功，且已撤销上一轮迁移 refresh_token
--   {0, ''}          = 预校验失败，未做任何写入
--
-- Redis 原子执行整个脚本，确保“撤旧 + 签新 + 写映射”不可分割：多标签页并发兑换
-- 不会在 (uid, clientId) 上堆积多条迁移凭证。ARGV[7] 前缀在脚本内拼接 key 名，
-- 与既有实现一致，以单实例 Redis 为前提。

-- 将 KEYS/ARGV 绑定为可读局部变量（仅别名，不影响脚本执行）
local current_key = KEYS[1]             -- 当前迁移凭证映射 key
local access_key = KEYS[2]              -- 新 access_token 记录 key
local refresh_key = KEYS[3]             -- 新 refresh_token 记录 key
local uid_index_key = KEYS[4]           -- 该用户 OAuth 令牌反向索引 Set

local access_json = ARGV[1]             -- 新 access 记录 JSON
local refresh_json = ARGV[2]            -- 新 refresh 记录 JSON
local access_ttl_arg = ARGV[3]          -- 新 access TTL（秒）
local refresh_ttl_arg = ARGV[4]         -- 新 refresh TTL（秒）
local access_member = ARGV[5]           -- 新 access 索引成员
local refresh_member = ARGV[6]          -- 新 refresh 索引成员
local refresh_prefix = ARGV[7]          -- refresh 记录 key 前缀
local refresh_member_prefix = ARGV[8]   -- refresh 索引成员前缀
local new_refresh = ARGV[9]             -- 新 refresh_token 明文
local map_ttl_arg = ARGV[10]            -- 映射 key TTL（秒）

-- 第一步：预校验。Lua 原子执行但不回滚已运行的命令，因此写入前先检查
-- 所有可能导致后续命令失败的条件。
local access_ttl = tonumber(access_ttl_arg)
local refresh_ttl = tonumber(refresh_ttl_arg)
local map_ttl = tonumber(map_ttl_arg)
if not access_ttl or access_ttl <= 0
        or not refresh_ttl or refresh_ttl <= 0
        or not map_ttl or map_ttl <= 0 then
    return {0, ''}
end
local index_type = redis.call('TYPE', uid_index_key).ok
if index_type ~= 'none' and index_type ~= 'set' then
    return {0, ''}
end
if redis.call('EXISTS', access_key) ~= 0 or redis.call('EXISTS', refresh_key) ~= 0 then
    return {0, ''}
end

-- 第二步：撤销上一轮迁移凭证（仅当映射存在）
local revoked = ''
local old_refresh = redis.call('GET', current_key)
if old_refresh then
    redis.call('DEL', refresh_prefix .. old_refresh)
    redis.call('SREM', uid_index_key, refresh_member_prefix .. old_refresh)
    revoked = old_refresh
end

-- 第三步：写入新令牌对并登记反向索引
redis.call('SET', access_key, access_json, 'EX', access_ttl)
redis.call('SET', refresh_key, refresh_json, 'EX', refresh_ttl)
redis.call('SADD', uid_index_key, access_member)
redis.call('SADD', uid_index_key, refresh_member)

-- 第四步：更新“当前迁移凭证”映射，供下一轮替换时撤销
redis.call('SET', current_key, new_refresh, 'EX', map_ttl)

return {1, revoked}
