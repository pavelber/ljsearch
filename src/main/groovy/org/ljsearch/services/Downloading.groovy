package org.ljsearch.services

import org.ljsearch.katkov.lj.LJRuntimeException
import org.ljsearch.katkov.lj.XMLRPCClient
import org.ljsearch.katkov.lj.xmlrpc.arguments.GetEventsArgument
import org.ljsearch.katkov.lj.xmlrpc.results.BlogEntry
import org.ljsearch.entity.IJournalRepository
import org.ljsearch.entity.Journal
import org.ljsearch.lucene.IIndexer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Service

import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Created by Pavel on 9/29/2015.
 */
@Service
@Scope("prototype")
class Downloading implements Runnable {

    private  static Logger logger = LoggerFactory.getLogger(Downloading.class)
    private static final LocalDate START_DATE = LocalDate.of(2000,7,1)

    Journal journal

    @Autowired
    XMLRPCClient  ljClient

    @Autowired
    IJournalRepository repo

    @Autowired
    IIndexer indexer

    def Downloading(Journal journal) {
        this.journal = journal
    }

    @Override
    void run() {
        while(true) {
            LocalDate maxDate = (journal.lastIndexed?fromDate(journal.lastIndexed): START_DATE).plus(1,ChronoUnit.DAYS);
            if (maxDate.isAfter(LocalDate.now())) {
                break;
            }
            GetEventsArgument argument = createArgument(maxDate)
            try {
                BlogEntry[] syncResult = ljClient.getevents(argument, 0);
                logger.info("Got {} entries from {}", syncResult.length, maxDate);

                syncResult.each {
                    indexer.add(it.subject, it.body, journal.journal, it.poster, it.permalink, it.date)
                }
                indexer.commit()
                journal.lastIndexed = toDate(maxDate)
                repo.save(journal)
            } catch (LJRuntimeException e) {
                logger.info("Got {}, going to sleep...",e.getCause().toString())
                Thread.sleep(10*60*1000)//TODO  constant
            }

        }
    }

    private toDate(LocalDate maxDate) {
        return Date.from(maxDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
    }

    private LocalDate fromDate(Date input){
        input.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private createArgument(LocalDate date) {
        GetEventsArgument argument = new GetEventsArgument();
        argument.setUsername(journal.username);
        argument.setHpassword(journal.password);
        argument.setSelecttype(GetEventsArgument.Type.DAY);
        argument.setHowmany(1000);
        argument.setYear(date.getYear())
        argument.setMonth(date.getMonth().value)
        argument.setDay(date.getDayOfMonth())
        argument.setUsejournal(journal.journal);
        return argument
    }
}
