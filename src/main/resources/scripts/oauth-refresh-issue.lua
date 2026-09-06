-- OAuth refresh_token 签发 access_token 脚本
--
-- 模型：refresh_token 为固定凭证，有效期内可反复使用；刷新只签发新 access_token，
-- 不删除、不换发 refresh_token。
--
-- KEYS:
--   KEYS[1] = refresh_token 记录 key（uc:oauth:refresh:{token}）
--   KEYS[2] = 新 access_token 记录 key（uc:oauth:access:{newToken}）
--   KEYS[3] = uid -> OAuth token 反向索引 Set（uc:uid:oauth:{uid}）
-- ARGV:
--   ARGV[1] = Java 已校验过的 refresh 原始 JSON（快照，用于并发比对）
--   ARGV[2] = 新 access 记录 JSON
--   ARGV[3] = 新 access TTL（秒）
--   ARGV[4] = 新 access 在反向索引中的成员（access:{newToken}）
--
-- Redis 原子执行整个脚本，确保“refresh 仍有效才签发新 access”：
-- 若 Java 校验后、脚本执行前 refresh 被吊销，此处 GET 不到匹配内容则拒绝签发。

-- 将 KEYS/ARGV 绑定为可读局部变量（仅别名，不影响脚本执行）
local refresh_key = KEYS[1]        -- refresh_token 记录 key（待校验仍有效）
local access_key = KEYS[2]         -- 新 access_token 记录 key
local uid_index_key = KEYS[3]      -- 该用户 OAuth 令牌反向索引 Set

local expected_json = ARGV[1]      -- Java 验证过的 refresh 内容快照
local access_json = ARGV[2]        -- 新 access 记录 JSON
local access_ttl_arg = ARGV[3]     -- 新 access TTL（秒）
local access_member = ARGV[4]      -- 新 access 索引成员

-- 第一步：校验 refresh 仍原封未动（已吊销/过期则放弃）
local current = redis.call('GET', refresh_key)
if not current or current ~= expected_json then
    return 0
end

-- 第二步：预校验。Lua 原子执行但不回滚已运行的命令，因此写入前先检查
-- 所有可能导致后续命令失败的条件。
local access_ttl = tonumber(access_ttl_arg)
if not access_ttl or access_ttl <= 0 then
    return -1
end
local index_type = redis.call('TYPE', uid_index_key).ok
if index_type ~= 'none' and index_type ~= 'set' then
    return -1
end
if redis.call('EXISTS', access_key) ~= 0 then
    return -1
end

-- 第三步：仅写入新 access 并登记反向索引，不触碰 refresh_token 记录
redis.call('SET', access_key, access_json, 'EX', access_ttl)
redis.call('SADD', uid_index_key, access_member)
return 1
