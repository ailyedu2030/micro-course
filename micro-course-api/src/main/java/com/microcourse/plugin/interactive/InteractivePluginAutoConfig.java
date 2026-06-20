package com.microcourse.plugin.interactive;

import com.microcourse.plugin.CourseTypePlugin;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@ConditionalOnProperty(name = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = false)
@EnableAsync
@ComponentScan("com.microcourse.plugin.interactive")
public class InteractivePluginAutoConfig {

    @Bean
    public CourseTypePlugin interactivePlugin() {
        return new InteractivePlugin();
    }

    @Bean(name = "slideRenderExecutor")
    public Executor slideRenderExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("slide-render-");
        executor.initialize();
        return executor;
    }
}
