package org.ljsearch.lucene

import groovy.transform.CompileStatic
import org.apache.commons.lang3.StringUtils
import org.apache.lucene.analysis.Analyzer
import org.apache.lucene.document.DateTools
import org.apache.lucene.document.Document
import org.apache.lucene.search.*
import org.apache.lucene.search.highlight.*
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.PropertySource
import org.springframework.stereotype.Service

import java.nio.file.Paths

/**
 * Created by Pavel on 10/5/2015.
 */
@Service
@PropertySource("classpath:ljsearch.properties")
@CompileStatic
class LuceneSearcher implements ISearcher {

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
    List<Post> search(String journal, String poster, String text, Date from, Date to) {

        synchronized (this) {//todo bad
            if (mgr == null) {
                init()
            }
        }

        mgr.maybeRefresh()
        def searcher = mgr.acquire()
        def results = []
        if (!StringUtils.isEmpty(journal) ||
                !StringUtils.isEmpty(poster) ||
                !StringUtils.isEmpty(text) ||
                from != null || to != null
        ) {
            try {
                Query q = QueryHelper.generate(text, journal, poster, from, to);
                SortField startField = new SortField(LuceneBinding.DATE_FIELD, SortField.Type.STRING_VAL, true);

                Sort sort = new Sort(startField);
                Collector collector = TopFieldCollector.create(sort, 400, true, false, false);
                searcher.search(q, collector);
                ScoreDoc[] hits = collector.topDocs().scoreDocs;
                for (int i = 0; i < hits.length; ++i) {
                    int docId = hits[i].doc;
                    Document d = searcher.doc(docId);

                    def content = d.get(LuceneBinding.CONTENT_FIELD)


                    String citation = createCitation(q, content)
                    results << new Post(
                            title: d.get(LuceneBinding.TITLE_FIELD) ?: "<no title>",
                            journal: d.get(LuceneBinding.JOURNAL_FIELD),
                            poster: d.get(LuceneBinding.POSTER_FIELD),
                            url: d.get(LuceneBinding.URL_FIELD),
                            date: DateTools.stringToDate(d.get(LuceneBinding.DATE_FIELD)).time,
                            text: citation
                    )
                }
            } finally {
                mgr.release(searcher)
            }
        }
        return results
    }

    private createCitation(Query q, String content) {
        String citation
        def highlited = getHighlightedField(q, LuceneBinding.analyzer, LuceneBinding.CONTENT_FIELD, content)
        if (highlited != null) {
            citation = highlited
        } else {
            def line = content.split("\n")[0]
            citation = ((line.length()> MAX_LENGTH_FIRST_LINE)?line.substring(0,MAX_LENGTH_FIRST_LINE):line) + " ... "
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
