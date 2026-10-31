package com.orange.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.exception.BusinessException;
import com.orange.entity.po.EfAccountBinding;
import com.orange.entity.po.EfPlayerData;
import com.orange.entity.vo.efplayer.EfAccountVO;
import com.orange.entity.vo.efplayer.EfPlayerDataVO;
import com.orange.mapper.EfAccountBindingMapper;
import com.orange.mapper.EfPlayerDataMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 终末地玩家数据读取逻辑单元测试：覆盖绑定列表、归属校验与 JSON 解析
 *
 * @author UserCenter
 */
@ExtendWith(MockitoExtension.class)
class EfPlayerDataServiceImplTest {

    private static final long UID = 1001L;

    @Mock
    private EfAccountBindingMapper bindingMapper;

    @Mock
    private EfPlayerDataMapper playerDataMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 已有绑定关系时按绑定字段返回角色列表
     */
    @Test
    void listsBindingsForCurrentUser() {
        EfAccountBinding binding = new EfAccountBinding();
        binding.setRoleId("role-1");
        binding.setServerId("1");
        binding.setCreateTime(LocalDateTime.of(2026, 10, 30, 12, 0));
        binding.setUpdateTime(LocalDateTime.of(2026, 10, 30, 13, 0));
        when(bindingMapper.selectByOwnerOrderByUpdateTime(UID)).thenReturn(List.of(binding));

        List<EfAccountVO> accounts = service().listBoundAccounts(UID);

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getRoleId()).isEqualTo("role-1");
        assertThat(accounts.get(0).getServerId()).isEqualTo("1");
        assertThat(accounts.get(0).getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 10, 30, 13, 0));
    }

    /**
     * 没有绑定关系时返回空列表而不是报错
     */
    @Test
    void returnsEmptyListWhenNoBinding() {
        when(bindingMapper.selectByOwnerOrderByUpdateTime(UID)).thenReturn(Collections.emptyList());

        assertThat(service().listBoundAccounts(UID)).isEmpty();
    }

    /**
     * 未绑定该角色时拒绝读取，且不查询数据表
     */
    @Test
    void rejectsReadingUnboundRole() {
        when(bindingMapper.countBinding("role-1", "1", UID)).thenReturn(0);

        assertThatThrownBy(() -> service().getPlayerData(UID, "role-1", "1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 80008);

        verify(playerDataMapper, never()).selectByRoleAndServer(anyString(), anyString());
    }

    /**
     * 已绑定时返回解析后的 JSON 对象与保存时间
     */
    @Test
    void readsPlayerDataWhenBound() {
        when(bindingMapper.countBinding("role-1", "1", UID)).thenReturn(1);
        EfPlayerData data = new EfPlayerData();
        data.setRoleId("role-1");
        data.setServerId("1");
        data.setPlayerData("{\"base\":{\"name\":\"玩家\",\"level\":60}}");
        data.setUpdateTime(LocalDateTime.of(2026, 10, 30, 13, 0));
        when(playerDataMapper.selectByRoleAndServer("role-1", "1")).thenReturn(data);

        EfPlayerDataVO result = service().getPlayerData(UID, "role-1", "1");

        assertThat(result.getRoleId()).isEqualTo("role-1");
        assertThat(result.getServerId()).isEqualTo("1");
        assertThat(result.getPlayerData().get("base").get("name").asText()).isEqualTo("玩家");
        assertThat(result.getUpdateTime()).isEqualTo(LocalDateTime.of(2026, 10, 30, 13, 0));
    }

    /**
     * serverId 缺省时按空串定位，绑定存在但数据行缺失返回 null
     */
    @Test
    void treatsMissingServerIdAsEmptyAndReturnsNullWhenDataMissing() {
        when(bindingMapper.countBinding("role-1", "", UID)).thenReturn(1);
        when(playerDataMapper.selectByRoleAndServer("role-1", "")).thenReturn(null);

        assertThat(service().getPlayerData(UID, "role-1", null)).isNull();
    }

    /**
     * roleId 格式非法时在查询绑定前就拒绝
     */
    @Test
    void rejectsInvalidRoleId() {
        assertThatThrownBy(() -> service().getPlayerData(UID, "role id", null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 10002);

        verify(bindingMapper, never()).countBinding(anyString(), anyString(), anyLong());
    }

    /**
     * 构造被测服务：读取逻辑不依赖 Redis，Redis 客户端仅用于保存限流
     *
     * @return 服务实例
     */
    private EfPlayerDataServiceImpl service() {
        return new EfPlayerDataServiceImpl(bindingMapper, playerDataMapper, new ObjectMapper(), stringRedisTemplate);
    }
}
