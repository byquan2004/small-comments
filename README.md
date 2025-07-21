# small-comment(小众点评)

## 项目简介
模拟了类似大众点评的功能。项目支持商铺信息管理、用户评论、优惠券发放等核心功能，采用 Redis 缓存提升高并发场景下的性能表现。

# 环境
- java:17.X
- springboot:3.x
- redis-stack-server:latest
- mysql:8.x
- rabbitmq:3.12-management
- redisson:3.5.X
- mybatis-plus:3.5.X

## redis数据结构使用：
- string/json
- hash
- sortedSet
- bitmap
- hyperLogLog

## 主要功能：
1.用户登录
- hash结构存储登录信息
- json结构存储登录信息
- 拦截器token自刷新
2.商铺缓存（）
- 缓存三兄弟解决方案（缓存穿透、击穿、雪崩）
3.商铺秒杀券下单
- 优惠卷缓存
- synconized于spring transactionTemplate事务模版（适用于单机）
- sentnx+lua脚本释放锁实现分布式锁（适用于单机和集群）
- redisson分布式锁集成
- lua脚本判断用户下单秒杀券资格，rabbitmq保存订单
4.用户登录签到
- bitmap位图实现
5.页面访问量
- hyperLogLog实现
6.点赞、关注以及博客
- sortedSet保存点赞、关注信息 实现点赞排行
- sortedSet发布博客笔记+rabbit mq异步投递fans收件箱（推模式）

## 其他功能
- redis实现全局id生成器 可适用于订单id 和 优惠券id

## 其他资源(src/main/resources)
- [db](src/main/resources/db)
- [hmdp.sql](src/main/resources/db/hmdp.sql)
- [nginx.zip](src/main/resources/static/nginx.zip) docker部署nginx
- [ports.csv](src/main/resources/static/ports.csv)
- [SECOND_KILL_VOUCHER.lua](src/main/resources/static/SECOND_KILL_VOUCHER.lua)
- [Test Plan.jmx](src/main/resources/static/Test%20Plan.jmx)
- [UNLOCK.lua](src/main/resources/static/UNLOCK.lua)
