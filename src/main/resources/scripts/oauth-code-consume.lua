-- KEYS[1]: authorization code record
-- KEYS[2]: short-lived used marker
-- ARGV[1]: exact JSON value already validated by Java
-- ARGV[2]: used marker TTL in seconds
--
-- The comparison is essential: validation happens before this script runs. If another request
-- consumes or replaces the record in between, this request must not delete that newer state.
local current = redis.call('GET', KEYS[1])
if not current or current ~= ARGV[1] then
    return 0
end

redis.call('DEL', KEYS[1])
redis.call('SET', KEYS[2], '1', 'EX', ARGV[2])
return 1
