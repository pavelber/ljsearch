package org.ljsearch.services

import groovy.swing.factory.BeanFactory
import org.ljsearch.entity.IJournalRepository
import org.ljsearch.lucene.LuceneIndexer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * Created by Pavel on 9/29/2015.
 */
//@Configuration
//@EnableScheduling
class StartDownloads implements SchedulingConfigurer {

    static Logger logger = LoggerFactory.getLogger(StartDownloads.class)
    private static final long TEN_MINUTES = 10 * 60 * 1000L

    @Autowired
    IJournalRepository repo

    @Autowired
    ApplicationContext factory

    @Autowired
    LuceneIndexer indexer

    //TODO: take new w/o restart!
    @Bean
    public ThreadPoolTaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(20);
        scheduler.setThreadNamePrefix("task-");
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        return scheduler;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        def journals = repo.findAll()
        logger.info("Got {} journals", journals.size())
        journals.each {
            logger.info("Starting download for {} ", it.journal)
            taskRegistrar.addFixedRateTask(factory.getBean("downloading",it), TEN_MINUTES);
        }


    }
}
