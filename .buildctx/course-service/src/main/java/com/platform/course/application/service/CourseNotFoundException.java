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

class CourseNotFoundException extends RuntimeException {
    public CourseNotFoundException(CourseId id) {
        super("课程不存在: " + id);
    }
}
