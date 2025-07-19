-- 根据传进来的key拿到的“线程标识” 是否与传进来的“线程标识”一致
if(redis.call('get',KEYS[1]) == ARGV[1]) then
    return redis.call('del',KEYS[1])
else
    return 0
end