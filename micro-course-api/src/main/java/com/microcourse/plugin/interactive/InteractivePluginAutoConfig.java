package com.microcourse.plugin.interactive;

import com.microcourse.plugin.CourseTypePlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@ConditionalOnProperty(name = "plugin.interactive.enabled", havingValue = "true", matchIfMissing = true)
@EnableAsync
@ComponentScan("com.microcourse.plugin.interactive")
public class InteractivePluginAutoConfig {

    private static final Logger log = LoggerFactory.getLogger(InteractivePluginAutoConfig.class);

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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        log.info("[InteractivePlugin] slideRenderExecutor initialized: core=2, max=4, queue=50, callerRunsPolicy");
        return executor;
    }

    @Bean(name = "interactiveRestTemplate")
    public RestTemplate interactiveRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(30000);
        log.info("[InteractivePlugin] interactiveRestTemplate initialized: connectTimeout=5s, readTimeout=30s");
        return new RestTemplate(factory);
    }
}
