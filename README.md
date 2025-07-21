# small-comment(小众点评)

## 项目简介
模拟了类似大众点评的功能，包括商铺信息管理、博客笔记点赞关注、抢购秒杀卷等核心功能。项目基本包含了常用Redis数据结构，实现高并发场景下的并发安全解决、系统吞吐性能提高等。

## 环境
- java:17
- springboot:3
- redis:7.4.4
- mysql:8.3
- rabbitmq:3.12
- redisson:3.5
- mybatis-plus:3.5

## redis数据结构使用：
- string/json
- hash
- sortedSet
- bitmap
- hyperLogLog

## 主要功能：
1. 用户登录
- hash结构存储登录信息
- json结构存储登录信息
- 拦截器token自刷新
  
2. 商铺缓存
- 缓存三兄弟解决方案（缓存穿透、击穿、雪崩）
  
3. 商铺秒杀券下单
- 优惠卷缓存
- synconized+spring transactionTemplate事务模版（适用于单机）
- sentnx+lua脚本释放锁实现分布式锁（适用于单机和集群）
- redisson分布式锁集成
- lua脚本判断用户下单秒杀券资格，rabbitmq保存订单

4. 用户登录签到
- bitmap位图实现
  
5. 页面访问量
- hyperLogLog实现

6. 点赞、关注
- sortedSet保存点赞、关注信息 实现点赞排行

7. 发布博客笔记
- sortedSet存储fans的收件箱，rabbitmq异步推送

## 其他功能
- redis实现全局id生成器 可适用于订单、优惠券id生成

## 其他资源(src/main/resources)
- [db](src/main/resources/db)
- [hmdp.sql](src/main/resources/db/hmdp.sql)
- [docker-nginx](src/main/resources/static/nginx.zip) 
- [ports.csv](src/main/resources/static/ports.csv)
- [SECOND_KILL_VOUCHER.lua](src/main/resources/static/SECOND_KILL_VOUCHER.lua)
- [Test Plan.jmx](src/main/resources/static/Test%20Plan.jmx)
- [UNLOCK.lua](src/main/resources/static/UNLOCK.lua)
