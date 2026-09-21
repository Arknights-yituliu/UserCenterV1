package com.orange.service;

import com.orange.entity.dto.schedule.UserScheduleSaveRequest;
import com.orange.entity.vo.UserScheduleVO;

import java.util.List;

/** 用户排班表服务。 */
public interface UserScheduleService {

    Long saveSchedule(Long uid, UserScheduleSaveRequest request);

    List<UserScheduleVO> listOwnSchedules(Long uid, String userName);

    UserScheduleVO getSchedule(Long id);

    void deleteSchedule(Long uid, Long id);
}
