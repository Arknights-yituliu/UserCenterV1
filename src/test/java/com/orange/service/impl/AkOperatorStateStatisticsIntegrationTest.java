package com.orange.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.entity.vo.akoperator.OperatorStatisticsVO;
import com.orange.service.AkOperatorStateStatisticsService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 干员养成数据统计集成测试。
 *
 * <p>该测试会真实扫描 ak_operator_state 全表，并请求外部干员实装时间接口，属于耗时操作，
 * 且必须连到有真实数据的数据库：这是全项目唯一的 {@code @SpringBootTest}，跑一次要全表扫描，
 * 不建议放进日常 {@code mvn test}。</p>
 *
 * <p>测试会先整表替换写入 ak_operator_state_statistics，再回读一次，因此跑完它之后公开查询接口
 * （{@code GET /ak-operator-statistics}）就有数据了。统计结果会完整打印到测试控制台，便于人工核对；
 * 同时做基础自检，避免「能跑但结果错」。</p>
 *
 * @author UserCenter
 */
@SpringBootTest
class AkOperatorStateStatisticsIntegrationTest {

    @Autowired
    private AkOperatorStateStatisticsService statisticsService;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 执行全量统计并落库，再回读结果打印，验证「统计 → 落库 → 读表」整条链路
     *
     * @throws Exception JSON 序列化失败时抛出
     */
    @Test
    void refreshThenReadBack() throws Exception {
        long startAt = System.currentTimeMillis();
        int written = statisticsService.refreshOperatorStatistics();
        long costMillis = System.currentTimeMillis() - startAt;

        List<OperatorStatisticsVO> result = statisticsService.getOperatorStatistics();
        assertThat(result).as("回读的条数应与写入的条数一致").hasSize(written).isNotEmpty();

        // 与 service 内部自检口径一致：各分布之和必须等于记录总数
        for (OperatorStatisticsVO vo : result) {
            assertThat(sum(vo.getElite()))
                    .as("干员 %s 的精英化分布之和应等于 own", vo.getCharId())
                    .isEqualTo(vo.getOwn());
        }

        long totalOwn = result.stream().mapToLong(OperatorStatisticsVO::getOwn).sum();
        System.out.println("==== 干员数据统计结果 ====");
        System.out.printf("干员数：%d，记录总数：%d，统计耗时：%d ms%n", result.size(), totalOwn, costMillis);
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        System.out.println("==== 干员数据统计结果结束 ====");
    }

    /**
     * 计算取值分布中各档位计数之和
     *
     * @param distribution 取值分布
     * @return 各档位计数之和
     */
    private static long sum(Map<String, Long> distribution) {
        return distribution.values().stream().mapToLong(Long::longValue).sum();
    }
}
