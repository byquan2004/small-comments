package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_TTL;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * 
 */
@Service
@RequiredArgsConstructor
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    private final StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {

        String typeList = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        if(!ObjectUtils.isEmpty(typeList)) {
            return Result.ok(JSONUtil.toList(typeList, ShopType.class));
        }
        List<ShopType> list = lambdaQuery().orderByDesc(ShopType::getSort).list();
        if(ObjectUtils.isEmpty(list)) {
            return Result.fail("未查询到商铺类型数据");
        }
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(list),
                CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);
        return Result.ok(list);
    }
}
