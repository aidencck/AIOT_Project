package com.aiot.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Redis 工具类
 * 规范： aiot:{module}:{entity}:{id}
 */
/**
 * Spring核心注解说明：
 * 1. @Component：通用组件注解，底层基于Spring的组件扫描机制（ClassPathBeanDefinitionScanner）扫描注解类，
 *      通过ASM字节码分析生成BeanDefinition并注册到BeanFactory。
 *      生命周期：默认随容器启动实例化（非懒加载情况下），单例模式下在容器整个生命周期存活，常驻内存，仅容器销毁时销毁。
 *      使用场景：无法归类到Controller/Service/Repository的通用工具类、配置类辅助组件等基础组件。
 * 2. @Controller：Web层专用组件注解，本质是@Component的派生注解（@Controller源码中包含@Component元注解），
 *      底层由SpringMVC的RequestMappingHandlerMapping识别，仅该注解修饰的类会被处理HTTP请求映射。
 *      生命周期：默认单例，容器启动时实例化，服务端运行期间一直存在，随容器销毁销毁。时空特性：单例模式下全局仅一个实例，所有Web请求共享该实例，需保证线程安全。使用场景：MVC模式中接收前端请求、调用Service层、返回视图或响应数据的Web处理类。
 * 3. @Service：业务逻辑层专用组件注解，同为@Component派生注解，核心作用是语义化区分业务层组件，底层和@Component的扫描注册逻辑完全一致。
 *      生命周期：默认单例，容器启动时实例化，随容器销毁而销毁。时空特性：单例模式下全局唯一实例，支撑所有业务线程的调用，需保证业务逻辑的线程安全。
 *      使用场景：封装核心业务流程、处理事务编排、调用DAO层数据操作的服务类，是业务逻辑的核心载体。
 * 4. @Repository：数据访问层专用组件注解，@Component派生注解，底层会自动注册PersistenceExceptionTranslationPostProcessor，
 *      将JDBC、MyBatis等持久化框架的原生数据库异常转换为Spring的DataAccessException统一异常体系。
 *      生命周期：默认单例，容器启动时实例化，随容器销毁销毁。时空特性：单例模式下全局唯一实例，被所有Service层线程共享调用。
 *      使用场景：DAO层的数据库操作类，仅用于封装数据增删改查逻辑，和持久化操作强绑定。
 * 5. @Configuration：配置类专用注解，底层通过ConfigurationClassPostProcessor处理，会对配置类生成CGLIB代理（full模式默认开启），
 *      保证@Bean标注的方法返回的实例始终是单例，替代传统XML配置文件。
 *      生命周期：配置类本身作为Bean会优先于普通Bean实例化，在容器启动早期就完成初始化，其内部@Bean定义的Bean的生命周期遵循Spring的Bean生命周期流程。
 *      时空特性：配置类实例在容器启动阶段优先创建，其定义的Bean的作用域由自身@Scope注解决定，默认单例。
 *      使用场景：整合第三方组件（如RedisTemplate、DataSource）、定义全局配置、装配Bean依赖的配置类，是Spring零XML配置的核心注解。
 * 6. @Autowired：依赖注入注解，底层由AutowiredAnnotationBeanPostProcessor处理，在Bean的属性注入阶段（populateBean方法中）按类型（byType）从BeanFactory中查找匹配的Bean，
 *      若存在多个同类型Bean会结合@Qualifier按名称筛选，支持构造方法、成员变量、Setter方法注入。
 *      生命周期：注入动作发生在Bean实例化之后、初始化之前（populateBean阶段），依赖的Bean必须提前完成实例化，否则会抛出NoSuchBeanDefinitionException。时空特性：注入的依赖实例的生命周期由被注入Bean的作用域决定，若被注入的是单例Bean，依赖的单例会和当前Bean同生命周期。使用场景：在当前Bean中引入依赖的其他Bean，替代硬编码的new创建方式，实现松耦合的依赖管理。
 * 7. @Value：配置属性注入注解，底层由AutowiredAnnotationBeanPostProcessor统一处理，支持${}语法从Environment（加载application.yml/properties等配置源）中读取属性，支持#{}SpEL表达式调用Spring容器中的Bean方法或计算值。
 *      生命周期：属性注入动作和@Autowired一致，发生在Bean的populateBean阶段，属性值在Bean初始化前完成注入。
 *      时空特性：注入的属性值是配置文件加载后的静态值，除非配置中心动态刷新（如Nacos），否则在Bean的生命周期内不会变更。使用场景：注入配置文件中的配置参数，如端口号、超时时间、业务开关等动态配置项。
 * 8. @Scope：Bean作用域指定注解，底层由BeanFactory处理，不同作用域的Bean生命周期和创建时机完全不同：singleton默认值，全局仅一个实例，容器启动时实例化，全程存活；prototype原型模式，每次获取Bean都创建新实例，由调用方管理生命周期，容器不负责销毁；
 *      request/session是Web环境特有的作用域，分别对应一次HTTP请求、一次用户会话的生命周期。
 *      时空特性：singleton空间上全局唯一，时间上和容器同生命周期；prototype空间上每次调用新建实例，时间上用完即可被GC回收；request仅在当前HTTP请求链内共享，session在同一个用户的多请求间共享。使用场景：需要多例的线程不安全类（如工具类存在可变成员变量）、Web环境的请求级上下文Bean、需要隔离状态的会话级Bean等。
 * 9. @Lazy：延迟初始化注解，底层会标记Bean的lazyInit属性为true，默认情况下Spring的非懒加载单例Bean会在容器启动时提前实例化，@Lazy修饰的Bean会延迟到首次被getBean或注入时才实例化。
 *      生命周期：实例化时机从容器启动阶段推迟到首次使用阶段，单例模式下实例化后仍和容器同生命周期存活。时空特性：空间上只有首次使用才会占用内存，时间上把启动阶段的初始化耗时分摊到首次调用时，提升容器启动速度。使用场景：启动过程中不需要立即用到的重量级Bean、仅在特定分支才会用到的条件性组件、需要避免启动循环依赖的场景。
 * 10. @Conditional：条件化注册注解，底层由ConditionEvaluator在BeanDefinition注册阶段调用matches方法，只有匹配条件的类才会被注册为Bean纳入Spring容器，Spring后续衍生出@ConditionalOnClass、@ConditionalOnProperty等更易用的派生注解。
 *      生命周期：条件判断发生在BeanDefinition注册阶段，不满足条件的类不会被注册，更不会被实例化，完全不存在于容器中。
 *      时空特性：不满足条件的Bean不会占用任何容器资源，从根源上避免不必要的内存占用和初始化耗时。
 *      使用场景：根据环境条件动态注册Bean，如生产环境启用某组件、测试环境启用另一个模拟组件，依赖的第三方类存在时才注册对应配置，配置文件中某开关开启时才启用某功能。
 */
@Component
public class RedisUtils {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${aiot.events.stream-maxlen:5000}")
    private long streamMaxLen;

    public RedisUtils(RedisTemplate<String, Object> redisTemplate, StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 构建符合规范的 Key
     */
    public String buildKey(String module, String entity, String id) {
        return String.format("aiot:%s:%s:%s", module, entity, id);
    }

    public void set(String key, Object value) {
        redisTemplate.opsForValue().set(key, value);
    }

    public void set(String key, Object value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    public boolean setIfAbsent(String key, Object value, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit));
    }

    public boolean setIfAbsentString(String key, String value, long timeout, TimeUnit unit) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, value, timeout, unit));
    }

    public boolean releaseIfHeld(String key, String expectedValue) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                Long.class);
        Long result = stringRedisTemplate.execute(script, Collections.singletonList(key), expectedValue);
        return Long.valueOf(1L).equals(result);
    }

    public Object get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public Object getAndDelete(String key) {
        return redisTemplate.opsForValue().getAndDelete(key);
    }

    public boolean delete(String key) {
        return Boolean.TRUE.equals(redisTemplate.delete(key));
    }

    public void publish(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
    }

    public RecordId addToStream(String streamKey, Map<String, String> body) {
        MapRecord<String, String, String> record = StreamRecords.string(body).withStreamKey(streamKey);
        RecordId recordId = stringRedisTemplate.opsForStream().add(record);
        // 近似裁剪到 maxlen（XTRIM MAXLEN ~）：防止流无界增长导致 Redis 内存膨胀，同时使 XLEN 稳定有界可观测。
        stringRedisTemplate.opsForStream().trim(streamKey, streamMaxLen, true);
        return recordId;
    }
}
