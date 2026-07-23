package com.microcourse.service;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.microcourse.entity.*;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

/**
 * 在纯 Mockito 测试中初始化 MyBatis-Plus TableInfoHelper，
 * 使得 LambdaQueryWrapper/LambdaUpdateWrapper 的 eq()/set() 等 lambda 方法可用。
 */
public final class MybatisPlusTestHelper {

    private static volatile boolean initialized = false;

    private MybatisPlusTestHelper() {}

    /** 初始化纯 Mockito 测试里常用的实体 TableInfo */
    public static void initTableInfo() {
        if (initialized) return;
        initialized = true;
        Configuration config = new Configuration();
        config.setMapUnderscoreToCamelCase(true);
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(config, "");
        assistant.setCurrentNamespace("com.microcourse.test");
        TableInfoHelper.initTableInfo(assistant, MicroSpecialty.class);
        TableInfoHelper.initTableInfo(assistant, MicroSpecialtyEnrollment.class);
        TableInfoHelper.initTableInfo(assistant, MicroSpecialtyTeacher.class);
        TableInfoHelper.initTableInfo(assistant, MicroSpecialtyCourse.class);
        TableInfoHelper.initTableInfo(assistant, MicroSpecialtyProposal.class);
        TableInfoHelper.initTableInfo(assistant, MicroSpecialtyFeaturedAudit.class);
        TableInfoHelper.initTableInfo(assistant, Enrollment.class);
        TableInfoHelper.initTableInfo(assistant, User.class);
        TableInfoHelper.initTableInfo(assistant, Video.class);
        TableInfoHelper.initTableInfo(assistant, MicroSpecialty.class);
    }
}
