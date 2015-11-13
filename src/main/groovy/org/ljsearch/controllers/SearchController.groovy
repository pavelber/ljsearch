package org.ljsearch.controllers

import groovy.json.JsonBuilder
import groovy.transform.CompileStatic
import org.apache.commons.lang3.StringUtils
import org.ljsearch.IndexedType
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
@CompileStatic
class SearchController {

    @Autowired
    protected ISearcher seacher


    @RequestMapping("/search")
    @ResponseBody
    String search(
            @RequestParam("term") String term,
            @RequestParam(value = "journal", required = false) String journal,
            @RequestParam(value = "poster", required = false) String poster,
            @RequestParam(value = "year", required = false) String yearStr,
            @RequestParam(value = "type", required = false) String type
    ) {
        journal = (journal == 'null' || journal == 'undefined') ? "" : journal
        Integer year = (yearStr == 'null' || yearStr == "" || yearStr == null || yearStr == 'undefined') ? null : Integer.parseInt(yearStr)
        Date from, to;
        if (year != null) {
            from = new GregorianCalendar(year, Calendar.JANUARY, 1).time
            to = new GregorianCalendar(year + 1, Calendar.JANUARY, 1).time
        }
        IndexedType indexedType = StringUtils.isEmpty(type) ? null : IndexedType.valueOf(type)
        def results = seacher.search(journal, poster, term, from, to, indexedType)
        return new JsonBuilder(results).toPrettyString()
    }
}
