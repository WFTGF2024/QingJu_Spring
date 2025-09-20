-- KEYS[1] = stock key
-- KEYS[2] = ordered set key
-- ARGV[1] = userId
-- 判断是否已下单
if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then
  return 2
end
-- 判断库存是否充足
local stock = tonumber(redis.call('get', KEYS[1]) or '0')
if stock <= 0 then
  return 1
end
-- 扣减库存并记录用户
redis.call('decr', KEYS[1])
redis.call('sadd', KEYS[2], ARGV[1])
return 0
