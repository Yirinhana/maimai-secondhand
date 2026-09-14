package com.maimai.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/** 静态资源：商品实拍图经服务端重编码后公开访问；路径本身不构成其他私有资源的授权。 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final MaimaiProperties properties;

    public WebConfig(MaimaiProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        String location = Path.of(properties.getUploadDir()).toAbsolutePath().normalize().toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location + "/");
    }
}
