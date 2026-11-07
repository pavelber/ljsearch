package org.ljsearch.services

import groovy.transform.CompileStatic
import org.ljsearch.DateUtils
import org.ljsearch.IndexedType
import org.ljsearch.comments.Comment
import org.ljsearch.comments.ICommentsClient
import org.ljsearch.entity.IJournalRepository
import org.ljsearch.entity.Journal
import org.ljsearch.katkov.lj.LJRuntimeException
import org.ljsearch.katkov.lj.XMLRPCClient
import org.ljsearch.katkov.lj.xmlrpc.arguments.GetDayCountsArgument
import org.ljsearch.katkov.lj.xmlrpc.arguments.GetEventsArgument
import org.ljsearch.katkov.lj.xmlrpc.results.BlogEntry
import org.ljsearch.katkov.lj.xmlrpc.results.DayCount
import org.ljsearch.lucene.IIndexer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Created by Pavel on 9/29/2015.
 */
@Service("postsdownloading")
@CompileStatic
class Downloading implements IDownloading {

    private static Logger logger = LoggerFactory.getLogger(Downloading.class)


    @Autowired
    XMLRPCClient ljClient

    @Autowired
    IJournalRepository repo

    @Autowired
    IIndexer indexer

    @Autowired
    ICommentsClient commentsClient


    @Override
    void download(Journal journal) {
        LocalDate lastAllowed = LocalDate.now().minusDays(2)
        // Days that have posts, from one getdaycounts call. Empty days are then
        // skipped without a getevents request each. null = unknown, go day by day.
        TreeSet<LocalDate> daysWithPosts = loadDaysWithPosts(journal)
        while (true) {
            LocalDate maxDate = (journal.last ? DateUtils.fromDate(journal.last) : DateUtils.START_DATE).plus(1, ChronoUnit.DAYS);
            if (maxDate.isAfter(lastAllowed)) {
                break
            }
            if (daysWithPosts != null && !daysWithPosts.contains(maxDate)) {
                LocalDate next = daysWithPosts.ceiling(maxDate)
                LocalDate lastEmpty = (next == null || next.isAfter(lastAllowed)) ? lastAllowed : next.minusDays(1)
                logger.info("{} : no entries from {} to {}, skipping", journal.journal, maxDate, lastEmpty)
                journal.last = DateUtils.toDate(lastEmpty)
                repo.save(journal)
                continue
            }
            GetEventsArgument argument = createArgument(journal, maxDate)
            try {
                BlogEntry[] syncResult = ljClient.getevents(argument, 0);
                logger.info("{} : Got {} entries from {}", journal.journal, syncResult.length, maxDate);
                Thread.sleep(DateUtils.SMALL_DELAY)
                syncResult.each { BlogEntry it ->
                    indexEntry(journal, it)
                }
                indexer.commit()
                journal.last = DateUtils.toDate(maxDate)
                repo.save(journal)
            } catch (LJRuntimeException e) {
                logger.error("Exception going to sleep...", e)
                Thread.sleep(DateUtils.DELAY)
                break
            }

        }
    }

    /**
     * Indexes one post and its comments. A failure here (bad date, odd markup,
     * empty body) is logged and skipped, so one broken post can neither stop
     * the journal nor be retried forever.
     */
    private void indexEntry(Journal journal, BlogEntry entry) {
        try {
            indexer.add(entry.subject, entry.body, journal.journal, entry.poster, entry.permalink, entry.date, IndexedType.Post)
        } catch (Exception e) {
            logger.error("Failed to index post " + entry.permalink, e)
            return
        }
        Collection<Comment> comments = [] as List<Comment>
        try {
            logger.info(entry.permalink)
            comments = commentsClient.getComments(entry.permalink)
            Thread.sleep(DateUtils.SMALL_DELAY)
        } catch (IOException e) {
            logger.info("Got {}, going to sleep...", e.getCause())
            Thread.sleep(DateUtils.DELAY)
        } catch (InterruptedException e) {
            throw e
        } catch (Exception e) {
            logger.error("Failed to get comments of " + entry.permalink, e)
            Thread.sleep(DateUtils.SMALL_DELAY)
        }
        comments.each { Comment comment ->
            if (comment.text == null) {
                return
            }
            try {
                indexer.add("", comment.text, journal.journal, comment.user, comment.link,
                        comment.date ?: entry.date, IndexedType.Comment)
            } catch (Exception e) {
                logger.error("Failed to index comment " + comment.link, e)
            }
        }
    }

    private TreeSet<LocalDate> loadDaysWithPosts(Journal journal) {
        GetDayCountsArgument argument = new GetDayCountsArgument()
        argument.setUsername(journal.user.username)
        argument.setHpassword(journal.user.password)
        argument.setUsejournal(journal.journal)
        try {
            DayCount[] counts = ljClient.getdaycounts(argument, 0)
            Thread.sleep(DateUtils.SMALL_DELAY)
            TreeSet<LocalDate> days = new TreeSet<LocalDate>()
            counts.each { DayCount it ->
                if (it.count > 0) {
                    days.add(DateUtils.fromDate(it.date))
                }
            }
            logger.info("{} : {} days with entries", journal.journal, days.size())
            // An empty answer may mean "no access" rather than "no posts";
            // do not skip anything on it.
            return days.isEmpty() ? null : days
        } catch (LJRuntimeException e) {
            logger.warn("{} : getdaycounts failed ({}), going day by day", journal.journal, e.toString())
            Thread.sleep(DateUtils.SMALL_DELAY)
            return null
        }
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
