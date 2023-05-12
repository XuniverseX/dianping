package com.dianping.service.impl;

import cn.hutool.json.JSONUtil;
import com.dianping.dto.Result;
import com.dianping.entity.ShopType;
import com.dianping.mapper.ShopTypeMapper;
import com.dianping.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryTypeList() {
        // String实现
//        String json = stringRedisTemplate.opsForValue().get("shop-type");
//        if (StrUtil.isNotBlank(json)) {
//            List<ShopType> shopTypeList = JSONUtil.toList(json, ShopType.class);
//            return Result.ok(shopTypeList);
//        }
//
//        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
//        if (shopTypeList == null) {
//            return Result.fail("商品列表为空!");
//        }
//
//        stringRedisTemplate.opsForValue().set("shop-type", JSONUtil.toJsonStr(shopTypeList));
//
//        return Result.ok(shopTypeList);

        // List实现
//        List<String> list = stringRedisTemplate.opsForList().range("shop-type", 0, -1);
//        if (list != null && !list.isEmpty()) {
//            List<ShopType> res = new ArrayList<>();
//            for (String s : list) {
//                res.add(JSONUtil.toBean(s, ShopType.class));
//            }
//            return Result.ok(res);
//        }
//
//        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
//        if (shopTypeList == null) {
//            return Result.fail("商品列表为空!");
//        }
//        for (ShopType shopType : shopTypeList) {
//            stringRedisTemplate.opsForList().rightPushAll("shop-type", JSONUtil.toJsonStr(shopType));
//        }
//        return Result.ok(shopTypeList);

        // ZSet实现
        Set<String> set = stringRedisTemplate.opsForZSet().range("shop-type", 0, 9);
        if (set != null && !set.isEmpty()) {
            List<ShopType> shopTypes = new ArrayList<>();
            for (String s : set) {
                shopTypes.add(JSONUtil.toBean(s, ShopType.class));
            }
            return Result.ok(shopTypes);
        }

        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        if (shopTypeList == null) {
            return Result.fail("商品列表为空!");
        }

        for (ShopType shopType : shopTypeList) {
            stringRedisTemplate.opsForZSet().add("shop-type", JSONUtil.toJsonStr(shopType), shopType.getSort());
        }

        return Result.ok(shopTypeList);
    }
}
