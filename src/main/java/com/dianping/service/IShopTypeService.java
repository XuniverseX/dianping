package com.dianping.service;

import com.dianping.dto.Result;
import com.dianping.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

public interface IShopTypeService extends IService<ShopType> {

    Result queryTypeList();
}
