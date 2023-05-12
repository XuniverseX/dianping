package com.dianping.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dianping.dto.LoginFormDTO;
import com.dianping.dto.Result;
import com.dianping.entity.User;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();
}
