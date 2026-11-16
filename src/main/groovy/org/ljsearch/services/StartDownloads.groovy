package org.ljsearch.services

import groovy.transform.CompileStatic
import org.ljsearch.entity.IJournalRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service

/**
 * Created by Pavel on 9/29/2015.
 */
@Service
@CompileStatic
class StartDownloads extends StartDownloadsBase {

    @Autowired
    @Qualifier("postsdownloading")
    IDownloading downloading;

    @Autowired
    CommentsBackfill commentsBackfill

    @Override
    void run() {
        super.run()
        if (Thread.currentThread().isInterrupted()) {
            return
        }
        // after the journals, never in parallel with them
        try {
            commentsBackfill.run()
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt()
        } catch (Exception e) {
            logger.error("Comments back-fill failed", e)
        }
    }
}
