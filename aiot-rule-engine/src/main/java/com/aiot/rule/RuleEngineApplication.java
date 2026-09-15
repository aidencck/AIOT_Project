package com.aiot.rule;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 规则引擎应用启动类
 * 组件扫描全生命周期梳理：
 * 1. 扫描准备阶段：SpringApplication.run()启动时，SpringBoot初始化上下文，解析@SpringBootApplication、@ComponentScan注解，获取basePackages定义的扫描路径
 * 2. 资源解析阶段：ClassPathBeanDefinitionScanner遍历指定包路径下的.class文件，识别带有@Component、@Service、@Repository、@Controller等 stereotype注解的类，生成BeanDefinition并注册到容器的BeanDefinitionMap中
 * 3.  Bean生命周期预处理：对已注册的BeanDefinition执行BeanFactoryPostProcessor扩展处理，完成属性值填充、依赖关系预解析、条件注解(@Conditional系列)判断，筛选出需要实例化的Bean
 * 4. Bean实例化阶段：按依赖顺序实例化非懒加载的单例Bean，执行构造方法注入，实例化后存入Spring的单例缓存池(singletonObjects)
 * 5.  Bean初始化阶段：依次调用BeanPostProcessor的前置处理方法、@PostConstruct注解方法、InitializingBean的afterPropertiesSet()方法，完成Bean的初始化
 * 6. 容器就绪阶段：所有Bean初始化完成后，ApplicationContext刷新完成，应用开始对外提供服务，@EnableScheduling注解的定时任务开始按调度规则执行
 * 7. 容器销毁阶段：应用关闭时触发上下文销毁，调用DisposableBean的destroy()方法、@PreDestroy注解方法，释放Bean占用的资源，完成组件全生命周期的收尾
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(basePackages = {"com.aiot"})
@EnableScheduling
public class RuleEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(RuleEngineApplication.class, args);
    }
}
