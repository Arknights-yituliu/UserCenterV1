package com.orange.service;

import com.orange.entity.dto.schedule.UserScheduleSaveRequest;
import com.orange.entity.vo.UserScheduleVO;

import java.util.List;

/** 用户排班表服务。 */
public interface UserScheduleService {

    Long saveSchedule(Long uid, UserScheduleSaveRequest request);

    /** 按 uid 查询该用户自己的排班表列表，按更新时间倒序。 */
    List<UserScheduleVO> listOwnSchedules(Long uid);

    List<UserScheduleVO> listOwnSchedules(Long uid, String userName);

    UserScheduleVO getSchedule(Long id);

    void deleteSchedule(Long uid, Long id);
}
