package org.ljsearch.services

import org.ljsearch.katkov.lj.XMLRPCClient
import org.ljsearch.katkov.lj.xmlrpc.arguments.GetEventsArgument
import org.ljsearch.katkov.lj.xmlrpc.results.BlogEntry
import org.ljsearch.entity.IJournalRepository
import org.ljsearch.entity.Journal
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Service

import java.time.LocalDate
import java.time.temporal.TemporalUnit

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

    def Downloading(Journal journal) {
        this.journal = journal
    }

    @Override
    void run() {
        while(true) {
            LocalDate maxDate = (LocalDate.from(journal.lastIndexed) ?: DEFAULT_SYNC_DATE).plus(1,TemporalUnit.DAYS);
            if (maxDate.isAfter(LocalDate.now())) {
                break;
            }
            GetEventsArgument argument = createArgument(maxDate)
            BlogEntry[] syncResult = ljClient.getevents(argument, 0);
            logger.info("Got {} entries from {}", syncResult.length, maxDate);
            syncResult.each {
                if (it.date > maxDate) {
                    maxDate = date
                }
            }

            journal.lastIndexed = Date.from(maxDate.atStartOfDay(ZoneId.systemDefault()).toInstant())
            repo.save(journal)
        }
    }

    private createArgument(LocalDate date) {
        GetEventsArgument argument = new GetEventsArgument();
        argument.setUsername(journal.username);
        argument.setHpassword(journal.password);
        argument.setSelecttype(GetEventsArgument.Type.DAY);
        argument.setHowmany(1000);
        argument.setYear(date.getYear())
        argument.setMonth(date.getMonth())
        argument.setDay(date.getDayOfMonth())
        argument.setUsejournal(journal.journal);
        return argument
    }
}
