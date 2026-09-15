package com.aiot.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
/**
 * Mybatis-Plus 全局配置
 * 生效时机：Spring 容器启动阶段，该配置类会被 Spring 组件扫描自动加载，在应用上下文刷新阶段完成所有 Bean 的初始化和注册
 * 使用场景：所有引入该配置类所在模块的服务（如数据服务、业务服务等），只要引入了 Mybatis-Plus 依赖并能扫描到该配置，都会自动加载这些插件
 * 被服务使用的方式：所有使用 Mybatis-Plus 进行数据库操作的服务，都会自动应用这些插件能力：分页查询时自动生效分页插件的物理分页逻辑，更新带 @Version 字段的实体时自动触发乐观锁的版本校验机制
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 配置 MybatisPlus 插件
     * 该 Bean 会在 Spring 容器初始化时创建，注入到 Mybatis-Plus 的全局配置中，被所有 Mapper 共享使用
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 乐观锁插件：仅对声明 @Version 字段的实体生效，防止并发更新丢数据
        // 可配置维度：无额外核心参数，通过实体类的@Version注解自定义生效范围，仅支持Integer/Long/Date/Timestamp类型的版本字段
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        // 添加分页插件 (根据你的数据库类型修改 DbType，这里默认 MySQL)
        // 可配置维度：DbType指定数据库类型适配不同分页方言、可设置overflow(false/true)控制溢出总页数后是否处理、可设置maxLimit限制单页最大查询条数
        // 与其他配置的区别：该插件是Mybatis-Plus专属的物理分页实现，不同于原生Mybatis的逻辑分页（内存分页），性能更高；
        // 区别于业务层自行实现的分页工具，该插件与Mybatis-Plus生态无缝集成，自动适配lambda查询、自定义SQL等多种场景
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
