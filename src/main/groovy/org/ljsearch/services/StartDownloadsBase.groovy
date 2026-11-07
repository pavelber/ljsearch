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
        logger.info("Got {} journals", journals.size())
        for (journal in journals) {
            logger.info(" Starting download for {} ", journal.journal)
            try {
                downloading.download(journal);
            } catch (InterruptedException e) {
                // shutdown: stop the run, keep the flag for the scheduler
                Thread.currentThread().interrupt()
                return
            } catch (Exception e) {
                // one broken journal must not skip all the journals after it
                logger.error("Download failed for " + journal.journal, e)
            }
        }
    }

    abstract IDownloading getDownloading()
}
