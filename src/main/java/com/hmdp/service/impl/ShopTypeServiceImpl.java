package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        String json = stringRedisTemplate.opsForValue().get("shop-type");
        if (StrUtil.isNotBlank(json)) {
            List<ShopType> shopTypeList = JSONUtil.toList(json, ShopType.class);
            return Result.ok(shopTypeList);
        }

        List<ShopType> shopTypeList = query().list();
        if (shopTypeList == null) {
            return Result.fail("商品列表为空!");
        }

        stringRedisTemplate.opsForValue().set("shop-type", JSONUtil.toJsonStr(shopTypeList));

        return Result.ok(shopTypeList);

    }
}
