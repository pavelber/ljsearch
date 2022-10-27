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

        logger.info("Searching for " + text)

        synchronized (this) {//todo bad
            if (mgr == null) {
                init()
            }
        }

        mgr.maybeRefresh()
        def searcher = mgr.acquire()
        List<Post> results = []
        if (!StringUtils.isEmpty(journal) ||
                !StringUtils.isEmpty(poster) ||
                !StringUtils.isEmpty(text) ||
                type != null ||
                from != null || to != null
        ) {
            try {
                Query q = queryCreator.generate(text, journal, poster, from, to, type)
                SortField startField = new SortField(LuceneBinding.DATE_FIELD, SortField.Type.STRING_VAL, true)

                Sort sort = new Sort(startField)

                TopDocs docs = searcher.search(q, 400, sort)

                ScoreDoc[] hits = docs.scoreDocs
                for (int i = 0; i < hits.length; ++i) {
                    int docId = hits[i].doc
                    Document d = searcher.doc(docId)

                    def content = d.get(LuceneBinding.CONTENT_FIELD)


                    String citation = createCitation(q, content)
                    results << new Post(
                            title: d.get(LuceneBinding.TITLE_FIELD) ?: "<no title>",
                            journal: d.get(LuceneBinding.JOURNAL_FIELD),
                            poster: d.get(LuceneBinding.POSTER_FIELD),
                            url: d.get(LuceneBinding.URL_FIELD),
                            date: DateTools.stringToDate(d.get(LuceneBinding.DATE_FIELD)).time,
                            text: citation,
                            type: IndexedType.valueOf(d.get(LuceneBinding.TYPE_FIELD))
                    )
                }
            } finally {
                mgr.release(searcher)
            }
        }
        return results.stream().distinct().collect(Collectors.toList())
    }

    private createCitation(Query q, String content) {
        String citation
        def highlited = getHighlightedField(q, LuceneBinding.analyzer, LuceneBinding.CONTENT_FIELD, content)
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
        QueryScorer queryScorer = new QueryScorer(query);
        Highlighter highlighter = new Highlighter(formatter, queryScorer);
        highlighter.setTextFragmenter(new SimpleSpanFragmenter(queryScorer, 100));
        highlighter.setMaxDocCharsToAnalyze(Integer.MAX_VALUE);
        return highlighter.getBestFragment(analyzer, fieldName, fieldValue);
    }

}
