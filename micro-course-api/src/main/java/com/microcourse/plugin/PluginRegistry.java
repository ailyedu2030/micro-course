package com.microcourse.plugin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microcourse.entity.PluginGrant;
import com.microcourse.repository.PluginGrantRepository;
import com.microcourse.util.SecurityUtil;
import com.microcourse.util.SpringContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PluginRegistry {

    private static final Logger log = LoggerFactory.getLogger(PluginRegistry.class);

    private final Map<String, CourseTypePlugin> plugins = new ConcurrentHashMap<>();

    public PluginRegistry(List<CourseTypePlugin> pluginList) {
        if (pluginList != null) {
            for (CourseTypePlugin p : pluginList) {
                if (p.isEnabled()) {
                    plugins.put(p.getType(), p);
                }
            }
        }
    }

    public boolean isEnabled(String type) {
        return type != null && plugins.containsKey(type.toUpperCase());
    }

    public CourseTypePlugin getPlugin(String type) {
        return type != null ? plugins.get(type.toUpperCase()) : null;
    }

    public Set<String> getEnabledTypes() {
        return Collections.unmodifiableSet(plugins.keySet());
    }

    public List<CourseTypePlugin> getEnabledPlugins() {
        return List.copyOf(plugins.values());
    }

    /**
     * 校验用户是否有某插件类型的指定操作权限
     *
     * @param userId     用户 ID
     * @param pluginType 插件类型（如 VIDEO, INTERACTIVE等）
     * @param action     操作（预留，暂未按 action 细分）
     * @return true 有权限
     */
    public boolean hasGrant(Long userId, String pluginType, String action) {
        if (userId == null || pluginType == null) return false;
        // VIDEO 内置插件不需要 grant
        if ("VIDEO".equalsIgnoreCase(pluginType)) return true;
        // ADMIN 全部通行
        if (SecurityUtil.isAdmin()) return true;

        try {
            PluginGrantRepository grantRepo = SpringContextHolder.getBean(PluginGrantRepository.class);
            if (grantRepo == null) return false;

            Long count = grantRepo.selectCount(
                    new LambdaQueryWrapper<PluginGrant>()
                            .eq(PluginGrant::getGranteeId, userId)
                            .eq(PluginGrant::getPluginId, pluginType.toUpperCase())
            );
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("[PluginRegistry] hasGrant 查询失败 userId={} pluginType={}", userId, pluginType, e);
            return false;
        }
    }
}
