-- 注销邮箱验证码的原子校验、错误计数与一次性消费。
-- KEYS[1]: tcl:code:account-delete:{uid}
-- ARGV[1]: 绑定邮箱、来源客户端与输入验证码组合成的预期值
-- ARGV[2]: 允许的最大错误次数
-- 存储格式: 错误次数:绑定值；绑定值中的邮箱与客户端经过 Base64URL 编码。
-- 返回: 1=成功并消费，0=过期/不存在/记录无效，-1=错误，-2=错误达到上限并作废。
local stored = redis.call('GET', KEYS[1])
if not stored then
    return 0
end
local split = string.find(stored, ':', 1, true)
if not split then
    return 0
end
local failures = tonumber(string.sub(stored, 1, split - 1))
if not failures then
    return 0
end
if failures >= tonumber(ARGV[2]) then
    redis.call('DEL', KEYS[1])
    return -2
end
local value = string.sub(stored, split + 1)
if value == ARGV[1] then
    redis.call('DEL', KEYS[1])
    return 1
end
failures = failures + 1
if failures >= tonumber(ARGV[2]) then
    redis.call('DEL', KEYS[1])
    return -2
end
local ttl = redis.call('PTTL', KEYS[1])
if ttl <= 0 then
    return 0
end
-- 沿用原剩余有效期，输错验证码不会延长有效期。
redis.call('SET', KEYS[1], failures .. ':' .. value, 'PX', ttl)
return -1
