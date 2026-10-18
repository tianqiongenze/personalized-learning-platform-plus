package com.platform.course.application.service;

import com.platform.course.application.command.*;
import com.platform.course.application.query.*;
import com.platform.course.domain.event.DomainEvent;
import com.platform.course.domain.model.*;
import com.platform.course.domain.model.valueobjects.*;
import com.platform.course.domain.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 课程应用服务
 */
@Transactional

/**
 * 异常类
 */

class CourseCodeAlreadyExistsException extends RuntimeException {
    public CourseCodeAlreadyExistsException(String code) {
        super("课程编码已存在: " + code);
    }
}
