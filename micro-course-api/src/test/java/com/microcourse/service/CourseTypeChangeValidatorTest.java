package com.microcourse.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.exception.BusinessException;
import com.microcourse.plugin.interactive.entity.CourseSlide;
import com.microcourse.plugin.interactive.mapper.CourseSlideMapper;
import com.microcourse.service.impl.CourseTypeChangeValidatorImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;

/**
 * F-2026-08-10-07: 课程类型变更校验器单元测试
 *
 * 覆盖 V333 设计原则：类型创建后固定，切换需先清课件。
 */
class CourseTypeChangeValidatorTest {

    private final CourseSlideMapper courseSlideMapper = Mockito.mock(CourseSlideMapper.class);
    private final CourseTypeChangeValidator validator = new CourseTypeChangeValidatorImpl(courseSlideMapper);

    @BeforeAll
    static void initTableInfo() {
        MybatisPlusTestHelper.initTableInfo();
        Configuration cfg = new Configuration();
        cfg.setMapUnderscoreToCamelCase(true);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, CourseSlide.class);
    }

    @Test
    @DisplayName("同类型变更（old=new）→ 放行不查 DB")
    void shouldAllowSameTypeChange() {
        assertDoesNotThrow(() -> validator.validate(1L, "HTML_COURSEWARE", "HTML_COURSEWARE"));
        Mockito.verify(courseSlideMapper, Mockito.never()).selectCount(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("null 旧类型 → 视为初始创建放行")
    void shouldAllowNullOldType() {
        assertDoesNotThrow(() -> validator.validate(1L, null, "VIDEO"));
    }

    @Test
    @DisplayName("不同类型 + 无残留课件 → 放行")
    void shouldAllowTypeChangeWhenNoSlides() {
        Mockito.when(courseSlideMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        assertDoesNotThrow(() -> validator.validate(1L, "HTML_COURSEWARE", "VIDEO"));
    }

    @Test
    @DisplayName("不同类型 + 残留课件 → 抛 BusinessException")
    void shouldRejectTypeChangeWhenSlidesExist() {
        Mockito.when(courseSlideMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        BusinessException ex = assertThrows(BusinessException.class,
            () -> validator.validate(1L, "HTML_COURSEWARE", "VIDEO"));
        // 验证 message 含 "3" 和 "切换课程类型"
        String msg = ex.getMessage();
        org.junit.jupiter.api.Assertions.assertTrue(msg.contains("3"));
        org.junit.jupiter.api.Assertions.assertTrue(msg.contains("切换课程类型"));
    }

    @Test
    @DisplayName("PPT→VIDEO 切换 + 有课件 → 拒绝（防锚点孤儿）")
    void shouldRejectPptToVideoChange() {
        Mockito.when(courseSlideMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertThrows(BusinessException.class,
            () -> validator.validate(42L, "PPT_COURSEWARE", "VIDEO"));
    }
}