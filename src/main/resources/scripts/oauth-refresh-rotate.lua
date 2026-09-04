-- KEYS[1]: old refresh token record
-- KEYS[2]: new access token record
-- KEYS[3]: new refresh token record
-- KEYS[4]: uid -> OAuth token reverse index set
-- ARGV[1]: exact old refresh JSON already validated by Java
-- ARGV[2]: new access JSON
-- ARGV[3]: new access TTL in seconds
-- ARGV[4]: new refresh JSON
-- ARGV[5]: new refresh TTL in seconds
-- ARGV[6]: old refresh member in the reverse index
-- ARGV[7]: new access member in the reverse index
-- ARGV[8]: new refresh member in the reverse index
--
-- Redis executes the complete script atomically. Two requests may read the same old token, but
-- only the first can compare successfully; all later requests return 0 without writing anything.
local current = redis.call('GET', KEYS[1])
if not current or current ~= ARGV[1] then
    return 0
end

-- Lua scripts are atomic but Redis does not roll back commands that ran before a runtime error.
-- Validate every condition that could make a later command fail before deleting the old token.
local access_ttl = tonumber(ARGV[3])
local refresh_ttl = tonumber(ARGV[5])
if not access_ttl or access_ttl <= 0 or not refresh_ttl or refresh_ttl <= 0 then
    return -1
end
local index_type = redis.call('TYPE', KEYS[4]).ok
if index_type ~= 'none' and index_type ~= 'set' then
    return -1
end
if redis.call('EXISTS', KEYS[2], KEYS[3]) ~= 0 then
    return -1
end

redis.call('DEL', KEYS[1])
redis.call('SREM', KEYS[4], ARGV[6])
redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[3])
redis.call('SET', KEYS[3], ARGV[4], 'EX', ARGV[5])
redis.call('SADD', KEYS[4], ARGV[7], ARGV[8])
return 1
