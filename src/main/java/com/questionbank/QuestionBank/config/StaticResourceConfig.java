package com.questionbank.QuestionBank.config;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// Configuration to serve static media files from Data folder
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(StaticResourceConfig.class);

    @Value("${media.upload.path:Data}")
    private String dataPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path dataDir = Paths.get(dataPath).toAbsolutePath().normalize();

        registry.addResourceHandler("/Data/**")
                .addResourceLocations("file:" + dataDir.toString() + "/")
                .setCachePeriod(3600)
                .resourceChain(true);

        log.info("Serving Data folder from: {}", dataDir.toString());
        log.info("Data folder accessible at: http://localhost:8080/Data/");
    }
}
