package org.ljsearch.services

import groovy.transform.CompileStatic
import org.ljsearch.IndexedType
import org.ljsearch.comments.Comment
import org.ljsearch.comments.ICommentsClient
import org.ljsearch.entity.IJournalRepository
import org.ljsearch.entity.IRecordsRepository
import org.ljsearch.entity.Journal
import org.ljsearch.entity.Record
import org.ljsearch.katkov.lj.LJRuntimeException
import org.ljsearch.katkov.lj.XMLRPCClient
import org.ljsearch.katkov.lj.xmlrpc.arguments.GetEventsArgument
import org.ljsearch.katkov.lj.xmlrpc.results.BlogEntry
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Created by Pavel on 9/29/2015.
 */
@Service("download2db")
@CompileStatic
class DownloadingToDB implements IDownloading {

    private static Logger logger = LoggerFactory.getLogger(DownloadingToDB.class)
    private static final LocalDate START_DATE = LocalDate.of(2001, 1, 1)
    public static final int DELAY = 20 * 60 * 1000


    @Autowired
    XMLRPCClient ljClient

    @Autowired
    IJournalRepository repo

    @Autowired
    IRecordsRepository recordsRepository

    @Autowired
    ICommentsClient commentsClient


    @Override
    @Async
    void download(Journal journal) {
        while (true) {
            LocalDate maxDate = (journal.last ? fromDate(journal.last) : START_DATE).plus(1, ChronoUnit.DAYS);
            if (maxDate.isAfter(LocalDate.now().minusDays(2))) {
                break;
            }
            GetEventsArgument argument = createArgument(journal, maxDate)
            try {
                BlogEntry[] syncResult = ljClient.getevents(argument, 0);
                logger.info("{} : Got {} entries from {}", journal.journal, syncResult.length, maxDate);

                syncResult.each { BlogEntry it ->
                    logger.info(it.body)
                    recordsRepository.save(new Record(it.subject, it.body, journal.journal, it.poster, it.permalink, it.date, IndexedType.Post))
                    def comments
                    logger.info( it.permalink)
                    try {
                        comments = commentsClient.getComments(it.permalink)
                    } catch (FileNotFoundException e) {
                        comments = null
                    } catch (IOException e) {
                        logger.info("Got {}, going to sleep...", e.getCause())
                        Thread.sleep(DELAY)
                    }
                    if (comments != null) {
                        comments.each { Comment comment ->
                            if (comment.text != null && comment.user.length()<63)
                                recordsRepository.save(new Record("", comment.text, journal.journal, comment.user, comment.link, comment.date, IndexedType.Comment))
                        }
                    }
                }
                journal.last = toDate(maxDate)
                repo.save(journal)
            } catch (LJRuntimeException e) {
                logger.info("Got {}, going to sleep...", e.getCause().toString())
                Thread.sleep(DELAY)
            }

        }
    }

    private Date toDate(LocalDate maxDate) {
        return Date.from(maxDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
    }

    private LocalDate fromDate(Date input) {
        input.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private GetEventsArgument createArgument(Journal journal, LocalDate date) {
        GetEventsArgument argument = new GetEventsArgument();
        argument.setUsername(journal.user.username);
        argument.setHpassword(journal.user.password);
        argument.setSelecttype(GetEventsArgument.Type.DAY);
        argument.setHowmany(1000);
        argument.setYear(date.getYear())
        argument.setMonth(date.getMonth().value)
        argument.setDay(date.getDayOfMonth())
        argument.setUsejournal(journal.journal);
        return argument
    }
}
