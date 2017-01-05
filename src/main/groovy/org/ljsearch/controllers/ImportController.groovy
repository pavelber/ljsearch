package org.ljsearch.controllers

import groovy.transform.CompileStatic
import org.ljsearch.DateUtils
import org.ljsearch.entity.IJournalRepository
import org.ljsearch.entity.IRecordsRepository
import org.ljsearch.entity.Journal
import org.ljsearch.entity.Record
import org.ljsearch.lucene.IIndexer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Controller
@CompileStatic
class ImportController {


    private static Logger logger = LoggerFactory.getLogger(ImportController.class)

    @Autowired
    IRecordsRepository recordsRepo

    @Autowired
    IJournalRepository repo

    @Autowired
    IIndexer indexer

    @RequestMapping("/import")
    @ResponseBody
    String importFromDb(@RequestParam("journal") String journalName) {
        Journal journal = repo.findOne(journalName)
        while (true) {
            LocalDate maxDate = (journal.last ? DateUtils.fromDate(journal.last) : DateUtils.START_DATE).plus(1, ChronoUnit.DAYS);
            if (maxDate.isAfter(LocalDate.now().minusDays(2))) {
                break
            }
            Set<Record> records = recordsRepo.findByJournalAndDateBetween(
                    journalName,
                    DateUtils.toDate(maxDate),
                    DateUtils.toDate(maxDate.plusDays(1)))
            logger.info("Importing $maxDate: ${records.size()}")
            records.each {
                indexer.add(it.title, it.text, journal.journal, it.poster, it.url, it.date, it.type)
            }
            indexer.commit()
            journal.last = DateUtils.toDate(maxDate)
            repo.save(journal)
        }

        return "done"
    }
}
