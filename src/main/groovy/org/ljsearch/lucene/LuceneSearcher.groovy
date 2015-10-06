package org.ljsearch.lucene

import groovy.transform.CompileStatic
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
class LuceneSearcher implements ISeacher {

    @Value('${index.dir}')
    protected String indexDir

    protected SearcherManager mgr


    def init() {
        Directory dir = FSDirectory.open(Paths.get(indexDir));
        mgr = new SearcherManager(dir, SearcherFactory.newInstance());
    }

    @Override
    List<Post> search(String journal, String poster, String text) {

        synchronized (this) {//todo bad
            if (mgr == null) {
                init()
            }
        }

        mgr.maybeRefresh()
        def searcher = mgr.acquire()
        def results = []
        try {
            Query q = QueryHelper.generate(text); //todo: use journal and poster  and date
            TopScoreDocCollector collector = TopScoreDocCollector.create(100);//TOdo 100
            searcher.search(q, collector);
            ScoreDoc[] hits = collector.topDocs().scoreDocs;
            for (int i = 0; i < hits.length; ++i) {
                int docId = hits[i].doc;
                Document d = searcher.doc(docId);

                def content = d.get(LuceneBinding.CONTENT_FIELD)

                results << new Post(
                        title: d.get(LuceneBinding.TITLE_FIELD) ?: "<no title>",
                        journal: d.get(LuceneBinding.JOURNAL_FIELD),
                        poster: d.get(LuceneBinding.POSTER_FIELD),
                        url: d.get(LuceneBinding.URL_FIELD),
                        date: DateTools.stringToDate(d.get(LuceneBinding.DATE_FIELD)).time,
                        text: getHighlightedField(q, LuceneBinding.analyzer, LuceneBinding.CONTENT_FIELD, content)
                        // todo: citation
                )
            }
        } finally {
            mgr.release(searcher)
        }

        return results
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
