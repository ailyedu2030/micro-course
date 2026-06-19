package com.microcourse.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * P0-3: 封面文件静态资源映射
 *
 * /api/files/** → file:./uploads/
 * 使封面 URL（如 /api/files/covers/{videoId}/{filename}）可直接由浏览器访问
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/files/**")
                .addResourceLocations("file:./uploads/");
    }
}
