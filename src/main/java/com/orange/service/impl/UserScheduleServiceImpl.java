package com.orange.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orange.common.enums.ResultCode;
import com.orange.common.exception.BadRequestException;
import com.orange.common.exception.BusinessException;
import com.orange.common.util.IdGenerator;
import com.orange.entity.dto.schedule.UserScheduleSaveRequest;
import com.orange.entity.po.UserInfo;
import com.orange.entity.po.UserSchedule;
import com.orange.entity.vo.UserScheduleVO;
import com.orange.mapper.UserInfoMapper;
import com.orange.mapper.UserScheduleMapper;
import com.orange.service.UserScheduleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/** 用户排班表服务实现。 */
@Service
public class UserScheduleServiceImpl implements UserScheduleService {

    private static final int MAX_SCHEDULES_PER_USER = 5;
    private static final int MAX_SCHEDULE_BYTES = 30 * 1024;

    private final UserScheduleMapper userScheduleMapper;
    private final UserInfoMapper userInfoMapper;
    private final ObjectMapper objectMapper;

    public UserScheduleServiceImpl(UserScheduleMapper userScheduleMapper,
                                   UserInfoMapper userInfoMapper,
                                   ObjectMapper objectMapper) {
        this.userScheduleMapper = userScheduleMapper;
        this.userInfoMapper = userInfoMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long saveSchedule(Long uid, UserScheduleSaveRequest request) {
        JsonNode schedule = parseSchedule(request.getSchedule());
        String normalizedSchedule = serialize(schedule);
        if (request.getId() != null) {
            UserSchedule current = userScheduleMapper.selectOwnedByIdForUpdate(request.getId(), uid);
            if (current == null) {
                throw new BusinessException(ResultCode.FORBIDDEN, "无权更新该排班表");
            }
            if (userScheduleMapper.updateOwnedSchedule(request.getId(), uid, normalizedSchedule) != 1) {
                throw new BusinessException(ResultCode.SYSTEM_ERROR, "更新排班表失败");
            }
            return request.getId();
        }

        // 锁住稳定存在的 user_info 行，保证多实例下同一用户创建操作串行，5 份上限不会被并发绕过。
        if (userInfoMapper.selectByUidForUpdate(uid) == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (userScheduleMapper.countByUid(uid) >= MAX_SCHEDULES_PER_USER) {
            throw new BadRequestException("每个用户最多保存 5 个排班表");
        }

        UserSchedule entity = new UserSchedule();
        entity.setId(IdGenerator.getInstance().nextId());
        entity.setUid(uid);
        entity.setSchedule(normalizedSchedule);
        if (userScheduleMapper.insert(entity) != 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "保存排班表失败");
        }
        return entity.getId();
    }

    @Override
    public List<UserScheduleVO> listOwnSchedules(Long uid, String userName) {
        if (userName == null || userName.isBlank()) {
            throw new BadRequestException("用户名不能为空");
        }
        UserInfo user = userInfoMapper.selectByUserName(userName);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_FOUND);
        }
        if (!uid.equals(user.getUid())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能查询自己的排班表");
        }
        return userScheduleMapper.selectList(Wrappers.<UserSchedule>lambdaQuery()
                        .eq(UserSchedule::getUid, uid)
                        .orderByDesc(UserSchedule::getUpdateTime))
                .stream()
                .map(this::toOwnerVO)
                .collect(Collectors.toList());
    }

    @Override
    public UserScheduleVO getSchedule(Long id) {
        UserSchedule entity = userScheduleMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "排班表不存在");
        }
        return toOwnerVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteSchedule(Long uid, Long id) {
        UserSchedule current = userScheduleMapper.selectOwnedByIdForUpdate(id, uid);
        if (current == null) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权删除该排班表");
        }
        if (userScheduleMapper.deleteOwnedById(id, uid) != 1) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "删除排班表失败");
        }
    }

    private JsonNode parseSchedule(String rawSchedule) {
        if (rawSchedule == null || rawSchedule.isBlank()) {
            throw new BadRequestException("排班表不能为空");
        }
        if (rawSchedule.getBytes(StandardCharsets.UTF_8).length > MAX_SCHEDULE_BYTES) {
            throw new BadRequestException("排班表不能超过 30KB");
        }
        try {
            JsonNode schedule = objectMapper.readTree(rawSchedule);
            if (schedule == null || !schedule.isArray()) {
                throw new BadRequestException("排班表必须是 JSON 数组");
            }
            for (JsonNode item : schedule) {
                if (!item.isObject()) {
                    throw new BadRequestException("排班表中的每项必须是 JSON 对象");
                }
            }
            return schedule;
        } catch (JsonProcessingException e) {
            throw new BadRequestException("排班表必须是合法 JSON");
        }
    }

    private JsonNode parseStoredSchedule(String rawSchedule) {
        try {
            return objectMapper.readTree(rawSchedule);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "排班表数据损坏");
        }
    }

    private String serialize(JsonNode schedule) {
        try {
            return objectMapper.writeValueAsString(schedule);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ResultCode.SYSTEM_ERROR, "序列化排班表失败");
        }
    }

    private UserScheduleVO toOwnerVO(UserSchedule entity) {
        UserScheduleVO vo = new UserScheduleVO();
        vo.setId(entity.getId());
        vo.setSchedule(parseStoredSchedule(entity.getSchedule()));
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
