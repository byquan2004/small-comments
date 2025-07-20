-- 优惠券下单资格校验 判断秒杀库存 一人一单，决定用户是否抢购成功
-- 1. 优惠券库存key
local voucherStockKey = KEYS[1]
-- 2. 已下单用户集合key
local orderSetKey = KEYS[2]
-- 3. 用户id
local userId = ARGV[1]

if(tonumber(redis.call("get", voucherStockKey)) <= 0) then
    -- 库存不足返回1
    return 1
end
if(redis.call("sismember", orderSetKey, userId) == 1) then
    -- 用户已经下单返回2
    return 2
end
-- 扣减库存
redis.call("decr", voucherStockKey)
-- 下单成功，保存用户信息
redis.call("sadd", orderSetKey, userId)
return 0