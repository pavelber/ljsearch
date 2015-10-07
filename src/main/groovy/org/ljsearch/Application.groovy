package org.ljsearch

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.context.web.SpringBootServletInitializer
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync

/**
 * Created by Pavel on 9/29/2015.
 */
@SpringBootApplication
@ComponentScan
@EnableJpaRepositories(basePackages = ["org.ljsearch"])
@EnableAsync
public class Application extends SpringBootServletInitializer  {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    // Used when deploying to a standalone servlet container
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(Application.class);
    }
}