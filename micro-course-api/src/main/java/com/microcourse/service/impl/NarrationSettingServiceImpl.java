package com.microcourse.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.dto.narration.NarrationSettingRequest;
import com.microcourse.dto.narration.NarrationSettingVO;
import com.microcourse.entity.NarrationSetting;
import com.microcourse.repository.NarrationSettingRepository;
import com.microcourse.service.NarrationSettingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class NarrationSettingServiceImpl implements NarrationSettingService {

    private final NarrationSettingRepository repository;

    public NarrationSettingServiceImpl(NarrationSettingRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public NarrationSettingVO getByCourseId(Long courseId) {
        LambdaQueryWrapper<NarrationSetting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NarrationSetting::getCourseId, courseId);
        NarrationSetting setting = repository.selectOne(wrapper);
        if (setting == null) {
            setting = createDefault(courseId);
        }
        return toVO(setting);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NarrationSettingVO save(Long courseId, NarrationSettingRequest request) {
        NarrationSetting setting = new NarrationSetting();
        setting.setSpeakerIdentity(request.getSpeakerIdentity());
        setting.setTargetAudience(request.getTargetAudience());
        setting.setSpeakingStyle(request.getSpeakingStyle());
        setting.setTotalDurationMinutes(request.getTotalDurationMinutes());
        setting.setUpdatedAt(LocalDateTime.now());

        LambdaQueryWrapper<NarrationSetting> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NarrationSetting::getCourseId, courseId);
        NarrationSetting existing = repository.selectOne(wrapper);

        if (existing != null) {
            setting.setId(existing.getId());
            setting.setCreatedAt(existing.getCreatedAt());
            repository.updateById(setting);
        } else {
            setting.setCourseId(courseId);
            setting.setCreatedAt(LocalDateTime.now());
            setting.setUpdatedAt(LocalDateTime.now());
            repository.insert(setting);
        }
        return toVO(setting);
    }

    @Override
    @Transactional(readOnly = true)
    public String buildSystemPrompt(Long courseId) {
        NarrationSetting setting = repository.selectOne(new LambdaQueryWrapper<NarrationSetting>().eq(NarrationSetting::getCourseId, courseId));
        if (setting == null) {
            setting = createDefault(courseId);
        }
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位经验丰富的").append(setting.getSpeakerIdentity()).append("。");
        sb.append("根据幻灯片内容生成适合口头讲述的讲解稿。");
        sb.append("目标受众是").append(setting.getTargetAudience()).append("。");
        sb.append("要求：①语气").append(setting.getSpeakingStyle()).append("，像在课堂上讲课 ");
        sb.append("②用口语化表达，避免书面语 ");
        sb.append("③重点解释概念而非照读文字 ");
            sb.append("④整个课件约").append(setting.getTotalDurationMinutes()).append("分钟讲完，重点页可多花时间，简单页一带而过 ");
        sb.append("⑤纯文本，不包含 Markdown 标记 ");
        sb.append("⑥开头可以直接进入主题，不要使用「同学」「大家好」等称呼开头");
        return sb.toString();
    }

    private NarrationSetting createDefault(Long courseId) {
        NarrationSetting setting = new NarrationSetting();
        setting.setCourseId(courseId);
        setting.setSpeakerIdentity("大学教师");
        setting.setTargetAudience("学生");
        setting.setSpeakingStyle("亲切自然，像在课堂上讲课");
        setting.setTotalDurationMinutes(15);
        return setting;
    }

    public static NarrationSettingVO toVO(NarrationSetting setting) {
        if (setting == null) return null;
        NarrationSettingVO vo = new NarrationSettingVO();
        vo.setId(setting.getId());
        vo.setCourseId(setting.getCourseId());
        vo.setSpeakerIdentity(setting.getSpeakerIdentity());
        vo.setTargetAudience(setting.getTargetAudience());
        vo.setSpeakingStyle(setting.getSpeakingStyle());
        vo.setTotalDurationMinutes(setting.getTotalDurationMinutes());
        vo.setUpdatedAt(setting.getUpdatedAt());
        return vo;
    }
}
