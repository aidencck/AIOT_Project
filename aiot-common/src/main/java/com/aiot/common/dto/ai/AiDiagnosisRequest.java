package com.aiot.common.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI诊断请求DTO - 用于接收前端/调用方发起的设备AI诊断请求参数
 * 
 * 【核心场景】
 * 1. 设备异常上报后触发的自动AI诊断流程：设备上报运行异常时，系统自动调用AI诊断接口，传入设备ID、场景类型和关联事件ID
 * 2. 人工发起的主动诊断：用户在平台主动对某台设备发起AI诊断，仅需传入设备ID和场景类型即可触发诊断流程
 * 3. 跨系统联动诊断：其他业务系统（如能耗管理、 predictive maintenance系统）调用本接口对设备进行批量诊断
 * 
 * 【底层流转逻辑】
 * 请求参数 -> 接口参数校验 -> 封装为内部诊断任务对象 -> 写入消息队列 -> AI消费服务拉取任务 -> 加载对应场景的诊断模型 -> 拉取设备实时/历史时序数据 -> 模型推理生成诊断结果 -> 回写诊断结果库并回调发起方
 * 
 * 【第一性原则设计】
 * 1. 最小必要参数：仅保留诊断流程必须的三个核心标识，避免冗余参数增加序列化 overhead 和校验复杂度
 * 2. 强制必填约束：deviceId（设备唯一身份，所有诊断的基础载体）、sceneType（场景是模型匹配的核心依据，不同场景的诊断逻辑完全独立）作为必传参数，从入口避免无效诊断请求
 * 3. 可选扩展参数：eventId作为可选参数，支持关联异常事件链路，同时不强制无事件场景下的诊断传参，兼顾链路追踪和灵活性
 * 4. 长度限制对齐底层存储：字段长度约束与数据库、消息队列的存储长度限制完全一致，从入口拦截超长参数避免底层存储异常
 */
// 常用注解说明：本DTO使用的注解分为Lombok简化代码注解、Jakarta Validation参数校验注解，以下是常用可选注解的补充说明
// 1. Lombok生态常用注解（可根据场景扩展）：
//    @NoArgsConstructor 生成无参构造方法，序列化/反序列化框架（如Jackson）通常要求类有无参构造
//    @AllArgsConstructor 生成全参构造方法，方便手动构建对象
//    @Builder 生成建造者模式代码，支持链式调用创建对象，适合多参数场景
//    @ToString.Exclude 排除字段不生成toString()，避免敏感信息打印或循环引用
//    @EqualsAndHashCode.Exclude 排除字段不参与equals/hashCode计算
// 2. Jakarta Validation参数校验常用注解（补充当前已用的@NotBlank/@Size）：
//    @NotNull 验证对象是否不为null（适用于非字符串类型，@NotBlank仅适用于字符串）
//    @NotEmpty 验证集合类字段是否非空，可用于List/Map等集合参数
//    @Min/@Max 验证数值类型的取值范围，如@Min(1) @Max(100) private Integer priority;
//    @Pattern 验证字符串是否匹配正则表达式，如@Pattern(regexp = "^[A-Za-z0-9_]+$", message = "deviceId只能包含字母数字下划线")
//    @Email 验证字符串是否符合邮箱格式
//    @Valid 级联校验，若字段是嵌套对象，添加@Valid可触发嵌套对象内部的校验规则
// 3. 序列化/其他常用注解：
//    @JsonProperty 指定JSON序列化/反序列化时的字段名，适配不同前后端字段命名规范
//    @JsonFormat 配置日期类型的序列化格式，如@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
//    @ApiModelProperty Swagger接口文档注解，用于生成接口参数说明，方便前端对接

@Data
// 补充常用的无参构造，适配大多数序列化框架要求
@NoArgsConstructor
// 补充全参构造，方便手动构建请求对象
@AllArgsConstructor
// 补充Builder建造者模式，支持链式创建对象，多参数场景下更易用
@Builder
public class AiDiagnosisRequest {
    /**
     * 设备唯一标识：所有AI诊断的核心主体，必传参数
     * 长度限制64位，对齐设备中心的设备ID编码规则
     */
    @NotBlank(message = "deviceId 不能为空")
    @Size(max = 64, message = "deviceId 长度不能超过64")
    // 新增格式校验：限制deviceId仅可包含字母、数字、下划线，符合系统设备ID编码规范
    @Pattern(regexp = "^[A-Za-z0-9_]+$", message = "deviceId只能包含字母、数字和下划线")
    private String deviceId;

    /**
     * 场景类型：用于匹配对应场景的AI诊断模型，必传参数
     * 不同场景（如空压机、光伏逆变器、冷水机组）的诊断特征、模型完全独立，是AI服务加载推理逻辑的核心依据
     * 长度限制32位，对齐场景字典表的编码长度
     */
    @NotBlank(message = "sceneType 不能为空")
    @Size(max = 32, message = "sceneType 长度不能超过32")
    private String sceneType;

    /**
     * 关联事件ID：可选参数，用于关联触发本次诊断的异常事件ID
     * 支持诊断流程的链路溯源，可将诊断结果与原异常事件绑定，实现事件全生命周期管理
     * 长度限制64位，对齐事件中心的事件ID编码规则
     */
    @Size(max = 64, message = "eventId 长度不能超过64")
    private String eventId;
}
