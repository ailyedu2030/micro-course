package com.microcourse.plugin.interactive.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microcourse.plugin.interactive.dto.CoursewareTreeDTO;
import com.microcourse.plugin.interactive.dto.SlidePageVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Q-2 (N+1 修复): 课件读侧 Redis 缓存层 —— 学生播放页 (SlidePageVO 列表) + 教师课件树 (CoursewareTreeDTO)。
 *
 * <p>
 * 问题: 大课件（30 页）首屏加载时 buildV2PptPages 逐页查 script + audio（2N SQL），
 * 已通过批量 Mapper 降到 2 SQL；但读侧热点仍可被 Redis 吸收：
 * 课程级 key <code>courseware:pages:{courseId}:{sectionId}:{chapterId}</code> → TTL 10 min.
 * </p>
 *
 * <p>
 * P14-C (N+1 修复): 教师课件树端点（getCoursewareTree/buildPptTree）同样有 DB 构建成本
 * （6+ SQL/请求），高并发（Load test TREE30@100）尾部延迟超预算。PPT 树无动态 nonce 可安全
 * 缓存：key <code>courseware:tree:{courseId}:{sectionId}:{chapterId}</code> → TTL 10 min。
 * HTML 树含动态 CSP nonce → 不缓存（与 pages 的 is_trusted 严格模式同理由）。
 * </p>
 *
 * <p>
 * 数据一致性: 10 min TTL 内如教师改脚本/音色，学生/教师可能拿到旧缓存（最多延迟 10 分钟）。
 * 写路径（upload / uploadHtmlFile / delete* / updatePage / flow 变更 / TTS 完成）均调用
 * invalidateCourse 主动失效（教师操作后立即生效，无感知延迟）—— 本类 invalidateCourse 同时
 * 清 pages 与 tree 两个前缀，保证 tree 缓存随所有写操作失效。
 * </p>
 *
 * <p>
 * 安全边界: 严格模式（is_trusted=false）的 HTML 课件读时注入动态 CSP nonce → 不缓存
 * （避免 nonce 固定），由调用方判断 cacheable。
 * </p>
 *
 * <p>
 * Redis key 命名:
 *   pages: mc:courseware:pages:{courseId}:s{sectionId}:c{chapterId}
 *   tree : mc:courseware:tree:{courseId}:s{sectionId}:c{chapterId}
 * Value: JSON (Jackson + JSR310) · TTL: 10 min
 * </p>
 */
@Component
public class CoursewarePagesCache {

    private static final Logger log = LoggerFactory.getLogger(CoursewarePagesCache.class);
    private static final String KEY_PREFIX = "mc:courseware:pages:";
    private static final String TREE_KEY_PREFIX = "mc:courseware:tree:";
    private static final Duration TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CoursewarePagesCache(ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                ObjectProvider<ObjectMapper> objectMapperProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
        this.objectMapper = objectMapperProvider.getIfAvailable();
    }

    private String key(Long courseId, Long sectionId, Long chapterId) {
        return KEY_PREFIX + courseId + ":s" + (sectionId == null ? "n" : sectionId)
                + ":c" + (chapterId == null ? "n" : chapterId);
    }

    private String treeKey(Long courseId, Long sectionId, Long chapterId) {
        return TREE_KEY_PREFIX + courseId + ":s" + (sectionId == null ? "n" : sectionId)
                + ":c" + (chapterId == null ? "n" : chapterId);
    }

    /**
     * 取缓存. Redis 不可用 / 反序列化失败时返回 empty (退化到 DB 构建).
     */
    public Optional<List<SlidePageVO>> get(Long courseId, Long sectionId, Long chapterId) {
        if (redisTemplate == null || objectMapper == null) {
            return Optional.empty();
        }
        try {
            String json = redisTemplate.opsForValue().get(key(courseId, sectionId, chapterId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, new TypeReference<List<SlidePageVO>>() { }));
        } catch (Exception e) {
            log.warn("[Pages-Cache] GET failed (fallback to DB build): course={} err={}",
                    courseId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 写缓存. 失败不抛 (best-effort).
     */
    public void put(Long courseId, Long sectionId, Long chapterId, List<SlidePageVO> pages) {
        if (redisTemplate == null || objectMapper == null || pages == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(pages);
            redisTemplate.opsForValue().set(key(courseId, sectionId, chapterId), json, TTL);
        } catch (Exception e) {
            log.warn("[Pages-Cache] PUT failed: course={} err={}", courseId, e.getMessage());
        }
    }

    /**
     * P14-C: 取教师课件树缓存（PPT 分支）。Redis 不可用 / 反序列化失败时返回 empty (退化到 DB 构建).
     * 返回 null 表示缓存层不可用（调用方须判空，兼容 mock/退化场景）。
     */
    public Optional<CoursewareTreeDTO> getTree(Long courseId, Long sectionId, Long chapterId) {
        if (redisTemplate == null || objectMapper == null) {
            return Optional.empty();
        }
        try {
            String json = redisTemplate.opsForValue().get(treeKey(courseId, sectionId, chapterId));
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, new TypeReference<CoursewareTreeDTO>() { }));
        } catch (Exception e) {
            log.warn("[Tree-Cache] GET failed (fallback to DB build): course={} err={}",
                    courseId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * P14-C: 写教师课件树缓存（PPT 分支，无动态 nonce）。失败不抛 (best-effort).
     */
    public void putTree(Long courseId, Long sectionId, Long chapterId, CoursewareTreeDTO tree) {
        if (redisTemplate == null || objectMapper == null || tree == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(tree);
            redisTemplate.opsForValue().set(treeKey(courseId, sectionId, chapterId), json, TTL);
        } catch (Exception e) {
            log.warn("[Tree-Cache] PUT failed: course={} err={}", courseId, e.getMessage());
        }
    }

    /**
     * 精确失效 (单 key).
     */
    public void invalidate(Long courseId, Long sectionId, Long chapterId) {
        if (redisTemplate == null) {
            return;
        }
        try {
            redisTemplate.delete(key(courseId, sectionId, chapterId));
        } catch (Exception e) {
            log.warn("[Pages-Cache] INVALIDATE failed: course={} err={}", courseId, e.getMessage());
        }
    }

    /**
     * 课程级失效: 删除该课程全部 pages + tree 缓存 key（upload / delete / update 课件时调用，
     * 教师操作后学生端/教师端立即看到新内容，无 TTL 延迟）。
     * P14-C: 同时清 tree 前缀 —— 所有写路径（含 TTS 完成 / flow 变更）都经过本方法，tree 缓存随之失效。
     */
    public void invalidateCourse(Long courseId) {
        if (redisTemplate == null) {
            return;
        }
        try {
            Set<String> keys = redisTemplate.keys(KEY_PREFIX + courseId + ":*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("[Pages-Cache] invalidateCourse failed: course={} err={}", courseId, e.getMessage());
        }
        try {
            Set<String> treeKeys = redisTemplate.keys(TREE_KEY_PREFIX + courseId + ":*");
            if (treeKeys != null && !treeKeys.isEmpty()) {
                redisTemplate.delete(treeKeys);
            }
        } catch (Exception e) {
            log.warn("[Tree-Cache] invalidateCourse failed: course={} err={}", courseId, e.getMessage());
        }
    }
}
