package com.orange.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.exception.BusinessException;
import com.orange.entity.dto.efplayer.EfPlayerDataSaveRequest;
import com.orange.entity.po.EfAccountBinding;
import com.orange.entity.po.EfPlayerData;
import com.orange.entity.vo.efplayer.EfAccountVO;
import com.orange.entity.vo.efplayer.EfPlayerDataVO;
import com.orange.mapper.EfAccountBindingMapper;
import com.orange.mapper.EfPlayerDataMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
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
        binding.setServerType("official");
        binding.setServerName("官服");
        binding.setNickName("管理员");
        binding.setCreateTime(LocalDateTime.of(2026, 10, 30, 12, 0));
        binding.setUpdateTime(LocalDateTime.of(2026, 10, 30, 13, 0));
        when(bindingMapper.selectByOwnerOrderByUpdateTime(UID)).thenReturn(List.of(binding));

        List<EfAccountVO> accounts = service().listBoundAccounts(UID);

        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getRoleId()).isEqualTo("role-1");
        assertThat(accounts.get(0).getServerId()).isEqualTo("1");
        assertThat(accounts.get(0).getServerType()).isEqualTo("official");
        assertThat(accounts.get(0).getServerName()).isEqualTo("官服");
        assertThat(accounts.get(0).getNickName()).isEqualTo("管理员");
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

    @ParameterizedTest
    @NullAndEmptySource
    void readsLatestPlayerDataWhenRoleIdOmitted(String roleId) {
        EfPlayerData latest = new EfPlayerData();
        latest.setRoleId("latest-role");
        latest.setServerId("2");
        latest.setPlayerData("{\"level\":60}");
        latest.setUpdateTime(LocalDateTime.of(2026, 11, 16, 12, 0));
        when(playerDataMapper.selectLatestByOwner(UID)).thenReturn(latest);

        EfPlayerDataVO result = service().getPlayerData(UID, roleId, null);

        assertThat(result.getRoleId()).isEqualTo("latest-role");
        assertThat(result.getServerId()).isEqualTo("2");
        assertThat(result.getPlayerData().get("level").asInt()).isEqualTo(60);
        assertThat(result.getUpdateTime()).isEqualTo(latest.getUpdateTime());
        verify(bindingMapper, never()).countBinding(anyString(), anyString(), anyLong());
        verify(playerDataMapper, never()).selectByRoleAndServer(anyString(), anyString());
    }

    @Test
    void rejectsDefaultReadWhenNoPlayerDataAvailable() {
        when(playerDataMapper.selectLatestByOwner(UID)).thenReturn(null);

        assertThatThrownBy(() -> service().getPlayerData(UID, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 10002)
                .hasMessage("当前用户暂无可用的终末地玩家数据");
    }

    @Test
    void rejectsServerIdWithoutRoleId() {
        assertThatThrownBy(() -> service().getPlayerData(UID, null, "1"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 10002);

        verify(playerDataMapper, never()).selectLatestByOwner(anyLong());
    }

    @Test
    void rejectsCorruptLatestPlayerData() {
        EfPlayerData latest = new EfPlayerData();
        latest.setRoleId("latest-role");
        latest.setServerId("1");
        latest.setPlayerData("invalid-json");
        when(playerDataMapper.selectLatestByOwner(UID)).thenReturn(latest);

        assertThatThrownBy(() -> service().getPlayerData(UID, null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", 40001);
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
     * 绑定行缺少展示字段时统一按空串返回，避免前端额外判空
     */
    @Test
    void normalizesMissingProfileFieldsToEmptyStrings() {
        EfAccountBinding binding = new EfAccountBinding();
        binding.setRoleId("role-1");
        binding.setServerId("");
        when(bindingMapper.selectByOwnerOrderByUpdateTime(UID)).thenReturn(List.of(binding));

        EfAccountVO account = service().listBoundAccounts(UID).get(0);

        assertThat(account.getServerType()).isEmpty();
        assertThat(account.getServerName()).isEmpty();
        assertThat(account.getNickName()).isEmpty();
    }

    /**
     * 保存玩家数据时把客户端上报的服务器展示信息透传给绑定 Mapper
     */
    @Test
    void passesServerProfileFieldsToBindingMapper() {
        allowSaveQuota();
        EfPlayerDataSaveRequest request = saveRequest("official", "官服");

        service().savePlayerData(UID, request);

        verify(bindingMapper).upsertBinding("role-1", "1", UID, "official", "官服");
    }

    /**
     * 未上报服务器展示信息时传 null，由 Mapper 的 COALESCE 沿用旧值
     */
    @Test
    void forwardsNullProfileFieldsWhenNotReported() {
        allowSaveQuota();
        EfPlayerDataSaveRequest request = saveRequest(null, null);

        service().savePlayerData(UID, request);

        verify(bindingMapper).upsertBinding(eq("role-1"), eq("1"), eq(UID), isNull(), isNull());
    }

    /**
     * 放行保存限流，使保存链路可以走到绑定 Mapper
     */
    @SuppressWarnings("unchecked")
    private void allowSaveQuota() {
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
    }

    /**
     * 构造保存请求：玩家数据为空 JSON 对象即可通过序列化校验
     *
     * @param serverType 服务器类型，可为 null
     * @param serverName 服务器名称，可为 null
     * @return 保存请求
     */
    private EfPlayerDataSaveRequest saveRequest(String serverType, String serverName) {
        EfPlayerDataSaveRequest request = new EfPlayerDataSaveRequest();
        request.setRoleId("role-1");
        request.setServerId("1");
        request.setServerType(serverType);
        request.setServerName(serverName);
        request.setPlayerData(new ObjectMapper().createObjectNode());
        return request;
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
