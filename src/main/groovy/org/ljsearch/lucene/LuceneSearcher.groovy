package org.ljsearch.lucene

import groovy.transform.CompileStatic
import org.apache.commons.lang3.StringUtils
import org.apache.log4j.Logger
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.document.DateTools
import org.apache.lucene.document.Document
import org.apache.lucene.search.*
import org.apache.lucene.search.highlight.*
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.BytesRef
import org.ljsearch.IndexedType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.PropertySource
import org.springframework.stereotype.Service

import java.nio.file.Paths
import java.util.stream.Collectors


@Service
@PropertySource("classpath:ljsearch.properties")
@CompileStatic
class LuceneSearcher implements ISearcher {

    private static final Logger logger = Logger.getLogger(LuceneSearcher.class.getName());

    @Autowired
    QueryCreator queryCreator

    public static final int MAX_LENGTH_FIRST_LINE = 100
    public static final int MAX_CHARS_TO_HIGHLIGHT = 50000
    @Value('${index.dir}')
    protected String indexDir

    protected SearcherManager mgr


    def init() {
        Directory dir = FSDirectory.open(Paths.get(indexDir));
        init(dir)
    }

    def init(Directory dir) {
        mgr = new SearcherManager(dir, SearcherFactory.newInstance());
    }

    @Override
    List<Post> search(String journal, String poster, String text, Date from, Date to, IndexedType type) {
        // the old API: the newest 400 hits in one response
        SearchPage page = searchPage(journal, poster, text, from, to, type, null, 400, false)
        return page.results.stream().distinct().collect(Collectors.toList())
    }

    /**
     * One page of hits, newest first. Paging uses a cursor (searchAfter), so
     * every page costs the same: the collector keeps only "limit" entries and
     * only those are loaded and highlighted. withStats adds counts per year
     * (collected in the same pass over the hits) and posts/comments (one extra
     * count query when no type filter is set).
     */
    @Override
    SearchPage searchPage(String journal, String poster, String text, Date from, Date to, IndexedType type,
                          String cursor, int limit, boolean withStats) {
        logger.info("Searching for " + text + (cursor ? " after " + cursor : ""))

        synchronized (this) {//todo bad
            if (mgr == null) {
                init()
            }
        }
        SearchPage page = new SearchPage()
        if (StringUtils.isEmpty(journal) && StringUtils.isEmpty(poster) && StringUtils.isEmpty(text) &&
                type == null && from == null && to == null) {
            return page
        }

        mgr.maybeRefresh()
        IndexSearcher searcher = mgr.acquire()
        try {
            Query q = queryCreator.generate(text, journal, poster, from, to, type)
            // Highlight only the words the user typed: the full query also
            // holds the OR over all public journals, the poster and the
            // date range, whose terms used to get marked inside the text.
            Query textQuery = StringUtils.isEmpty(text) ? null : queryCreator.generate(text)
            Sort sort = new Sort(new SortField(LuceneBinding.DATE_FIELD, SortField.Type.STRING_VAL, true))

            TopFieldCollector top = TopFieldCollector.create(sort, limit, parseCursor(cursor), true, false, false)
            YearCountCollector years = withStats ? new YearCountCollector() : null
            searcher.search(q, years == null ? (Collector) top : MultiCollector.wrap(top, years))
            TopDocs docs = top.topDocs()

            page.total = docs.totalHits
            ScoreDoc[] hits = docs.scoreDocs
            for (ScoreDoc hit : hits) {
                page.results << toPost(searcher.doc(hit.doc), textQuery)
            }
            if (hits.length == limit) {
                page.next = makeCursor((FieldDoc) hits[hits.length - 1])
            }
            if (withStats) {
                page.years = years.getCounts()
                if (type == null) {
                    page.posts = searcher.count(queryCreator.generate(text, journal, poster, from, to, IndexedType.Post))
                    page.comments = page.total - page.posts
                } else {
                    page.posts = type == IndexedType.Post ? page.total : 0
                    page.comments = type == IndexedType.Comment ? page.total : 0
                }
            }
        } finally {
            mgr.release(searcher)
        }
        return page
    }

    private Post toPost(Document d, Query textQuery) {
        def content = d.get(LuceneBinding.CONTENT_FIELD)
        return new Post(
                title: d.get(LuceneBinding.TITLE_FIELD) ?: "<no title>",
                journal: d.get(LuceneBinding.JOURNAL_FIELD),
                poster: d.get(LuceneBinding.POSTER_FIELD),
                url: d.get(LuceneBinding.URL_FIELD),
                date: DateTools.stringToDate(d.get(LuceneBinding.DATE_FIELD)).time,
                text: createCitation(textQuery, content ?: ""),
                type: IndexedType.valueOf(d.get(LuceneBinding.TYPE_FIELD))
        )
    }

    /* cursor = "<date sort value>_<doc id>" of the last hit of the previous page */
    static String makeCursor(FieldDoc last) {
        return ((BytesRef) last.fields[0]).utf8ToString() + "_" + last.doc
    }

    static FieldDoc parseCursor(String cursor) {
        if (StringUtils.isEmpty(cursor)) {
            return null
        }
        int i = cursor.lastIndexOf('_')
        if (i <= 0) {
            throw new IllegalArgumentException("bad cursor")
        }
        int doc = Integer.parseInt(cursor.substring(i + 1))
        return new FieldDoc(doc, Float.NaN, [new BytesRef(cursor.substring(0, i))] as Object[])
    }

    private String createCitation(Query q, String content) {
        String citation
        def highlited = q == null ? null : getHighlightedField(q, LuceneBinding.getAnalyzer(), LuceneBinding.CONTENT_FIELD, content)
        if (highlited != null) {
            citation = highlited
        } else {
            def line = content.split("\n")[0]
            citation = ((line.length() > MAX_LENGTH_FIRST_LINE) ? line.substring(0, MAX_LENGTH_FIRST_LINE) : line) + " ... "
        }
        return citation
    }

    private String getHighlightedField(Query query, Analyzer analyzer, String fieldName, String fieldValue) throws IOException, InvalidTokenOffsetsException {
        Formatter formatter = new SimpleHTMLFormatter("<mark>", "</mark>");
        QueryScorer queryScorer = new QueryScorer(query, fieldName);
        Highlighter highlighter = new Highlighter(formatter, queryScorer);
        highlighter.setTextFragmenter(new SimpleSpanFragmenter(queryScorer, 100));
        // Up to one page of hits (400 for the old API) is highlighted per search; analysing every text in
        // full was the slowest part. A match beyond this limit falls back to
        // the first line of the text.
        highlighter.setMaxDocCharsToAnalyze(MAX_CHARS_TO_HIGHLIGHT);
        return highlighter.getBestFragment(analyzer, fieldName, fieldValue);
    }

}
