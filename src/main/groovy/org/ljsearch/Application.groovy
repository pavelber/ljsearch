package org.ljsearch

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Created by Pavel on 9/29/2015.
 */
@SpringBootApplication
@ComponentScan
@EnableJpaRepositories(basePackages = ["org.ljsearch","org.ljsearch.katkov.lj"])
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}