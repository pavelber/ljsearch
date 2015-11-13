package org.ljsearch.services

import groovy.transform.CompileStatic
import org.ljsearch.entity.IJournalRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * Created by Pavel on 9/29/2015.
 */
@CompileStatic
abstract class StartDownloadsBase implements Runnable {

    static Logger logger = LoggerFactory.getLogger(StartDownloadsBase.class)

    @Autowired
    IJournalRepository repo


    @Override
    public void run() {
        def journals = repo.findAll()
        IDownloading downloading = getDownloading()
        logger.info("$name: Got {} journals", journals.size())
        journals.each {
            logger.info("$name: Starting download for {} ", it.journal)
            downloading.download(it);
        }
    }

    abstract IDownloading getDownloading()
    abstract String getName()
}