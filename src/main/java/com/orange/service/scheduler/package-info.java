/**
 * 定时任务调度层
 *
 * <p>约定：项目里所有 {@code @Scheduled} 只写在本包，业务类不再自己声明调度注解，
 * 只暴露可被调用的普通方法。这样做的好处是「什么时候跑」集中在一处：排查任务节奏、
 * 调整频率、核对线程池占用时不需要在 service 包里逐个文件搜注解。</p>
 *
 * <p>本包只做转发，不写业务逻辑：调度方法应当只有一行，把执行交给对应的 service。
 * 耗时任务必须像统计类任务那样「触发即返回」，真正的重活在后台线程执行，避免占住调度线程。</p>
 *
 * <p>当前任务清单：</p>
 * <ul>
 *   <li>{@link com.orange.service.scheduler.AkOperatorStateStatisticsScheduler} 干员养成统计，每小时 05 分</li>
 *   <li>{@link com.orange.service.scheduler.AkSkinStatisticsScheduler} 皮肤持有统计，每小时 10 分</li>
 *   <li>{@link com.orange.service.scheduler.EfPoolRecordTaskScheduler} 寻访导入任务，每秒 / token 覆盖，每分钟</li>
 *   <li>{@link com.orange.service.scheduler.OAuthClientOriginCacheScheduler} Origin 缓存对账，默认 60 秒</li>
 *   <li>{@link com.orange.service.scheduler.NicknameAuditScheduler} 昵称历史回溯审核，每 5 分钟</li>
 * </ul>
 *
 * @author UserCenter
 */
package com.orange.service.scheduler;
