package com.ooj.exam.service.impl;

import com.ooj.exam.entity.User;
import com.ooj.exam.mapper.UserMapper;
import com.ooj.exam.service.UserService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import org.springframework.stereotype.Service;

/**
 * 用户Service实现类
 * 实现用户相关的业务逻辑
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

} 