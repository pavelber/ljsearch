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
        Integer year = parseYear(yearStr)
        def results = seacher.search(clean(journal), clean(poster), clean(term), yearStart(year), yearEnd(year), parseType(type))
        return new JsonBuilder(results).toPrettyString()
    }

    /**
     * Paged search for the web page: {total, next, results, years, posts, comments}.
     * Pass the "next" value of a response as "after" to get the following page.
     * stats=true (meant for the first page) adds the counts per year and per type.
     */
    @RequestMapping("/api/search")
    @ResponseBody
    String searchPage(
            @RequestParam(value = "term", required = false) String term,
            @RequestParam(value = "journal", required = false) String journal,
            @RequestParam(value = "poster", required = false) String poster,
            @RequestParam(value = "year", required = false) String yearStr,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "after", required = false) String after,
            @RequestParam(value = "limit", required = false, defaultValue = "30") int limit,
            @RequestParam(value = "stats", required = false, defaultValue = "false") boolean stats
    ) {
        Integer year = parseYear(yearStr)
        int pageSize = Math.max(1, Math.min(limit, MAX_PAGE))
        def page = seacher.searchPage(clean(journal), clean(poster), clean(term), yearStart(year), yearEnd(year),
                parseType(type), clean(after), pageSize, stats)
        return new JsonBuilder(page).toString()
    }

    static final int MAX_PAGE = 100

    private static String clean(String s) {
        (s == null || s == 'null' || s == 'undefined') ? "" : s
    }

    private static Integer parseYear(String yearStr) {
        (yearStr == 'null' || yearStr == "" || yearStr == null || yearStr == 'undefined' || yearStr == 'NaN') ? null : Integer.parseInt(yearStr)
    }

    // Dates are indexed in UTC (DateTools), and the year statistics count UTC
    // years, so the year filter uses UTC bounds too; with the server time zone
    // a post from the first hours of January 1 fell into the previous year.
    private static Date yearStart(Integer year) {
        year == null ? null : utcJan1(year)
    }

    private static Date yearEnd(Integer year) {
        year == null ? null : utcJan1(year + 1)
    }

    private static Date utcJan1(int year) {
        Calendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"))
        c.clear()
        c.set(year, Calendar.JANUARY, 1)
        return c.time
    }

    private static IndexedType parseType(String type) {
        isEmpty(type) ? null : IndexedType.valueOf(type)
    }

    private static boolean isEmpty(String term) {
        StringUtils.isEmpty(term) || term == 'undefined'
    }
}
