package com.ooj.exam.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@MapperScan("com.ooj.exam.mapper")
public class MybatisPlusConfiguration {
    /**
     * 配置 MyBatis Plus 分页拦截器
     *
     * 工作原理：
     * 1. 拦截 SQL 执行，自动添加 LIMIT 分页语句
     * 2. 自动生成 COUNT 查询语句统计总数
     * 3. 支持多种数据库类型（MySQL、Oracle、PostgreSQL 等）
     *
     * @return MybatisPlusInterceptor 拦截器对象
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 添加分页拦截器，指定数据库类型为 MySQL
        PaginationInnerInterceptor paginationInterceptor = new PaginationInnerInterceptor(DbType.MYSQL);

        // 设置单页最大数量限制（防止恶意查询）
        paginationInterceptor.setMaxLimit(500L);

        // 添加拦截器到拦截器链
        interceptor.addInnerInterceptor(paginationInterceptor);

        return interceptor;
    }
}