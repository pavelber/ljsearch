package org.ljsearch

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.annotation.ComponentScan
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.scheduling.annotation.EnableAsync

@SpringBootApplication
@ComponentScan
@EnableJpaRepositories(basePackages = ["org.ljsearch"])
@EnableAsync
class Application extends SpringBootServletInitializer {

    static final String TLS_PROTOCOLS = "jdk.tls.client.protocols"

    static void main(String[] args) {
        pinTls12()
        SpringApplication.run(Application.class, args);
    }

    /**
     * LiveJournal (reached through the SOCKS tunnel) never answers a TLS 1.3
     * ClientHello: on 2026-11-17 every XML-RPC call from Java 17 hung in the
     * handshake until "Read timed out", while TLS 1.2 worked. Java 8 only
     * offered TLS 1.2, so the old version never hit it. Must run before the
     * first TLS connection; an explicit -Djdk.tls.client.protocols wins.
     */
    static void pinTls12() {
        if (System.getProperty(TLS_PROTOCOLS) == null) {
            System.setProperty(TLS_PROTOCOLS, "TLSv1.2")
        }
    }

    // Used when deploying to a standalone servlet container
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        pinTls12()
        return application.sources(Application.class)
    }
}
