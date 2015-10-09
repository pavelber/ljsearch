package org.ljsearch.controllers

import groovy.json.JsonBuilder
import org.ljsearch.lucene.ISearcher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

/**
 * Created by Pavel on 10/5/2015.
 */
@Controller
class SearchController {

    @Autowired
    protected ISearcher seacher


    @RequestMapping("/search")
    @ResponseBody
    String search(
            @RequestParam("term") String term,
            @RequestParam(value = "journal", required = false) String journal,
            @RequestParam(value = "poster", required = false) String poster,
            @RequestParam(value = "year", required = false) String yearStr
    ) {
        journal = (journal == 'null')?"":journal
        Integer year =  (yearStr == 'null' || yearStr == "" || yearStr == null)?null:Integer.parseInt(yearStr)
        Date from, to;
        if (year != null) {
            from = new GregorianCalendar(year, Calendar.JANUARY, 1).time
            to = new GregorianCalendar(year + 1, Calendar.JANUARY, 1).time
        }
        def results = seacher.search(journal, poster, term,from, to)
        return new JsonBuilder(results).toPrettyString()
    }
}
