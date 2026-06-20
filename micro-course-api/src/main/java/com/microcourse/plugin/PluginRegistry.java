package com.microcourse.plugin;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PluginRegistry {

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
}
