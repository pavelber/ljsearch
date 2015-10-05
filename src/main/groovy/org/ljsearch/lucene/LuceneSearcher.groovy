package org.ljsearch.lucene

import groovy.json.JsonBuilder
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexReader
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.TopScoreDocCollector
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
class LuceneSearcher implements ISeacher {

    @Value('${index.dir}')
    protected String indexDir

    protected IndexSearcher searcher


    def init() {
        IndexReader reader = DirectoryReader.open(FSDirectory.open(Paths.get(indexDir)));
        searcher = new IndexSearcher(reader);
    }

    @Override
    List<Post> search(String journal, String poster, String text) {

        synchronized (this) {//todo bad
            if (searcher==null){
                init()
            }
        }

        def results = []
        Query q = QueryHelper.generate(text); //todo: use journal and poster  and date
        TopScoreDocCollector collector = TopScoreDocCollector.create(100);//TOdo 100
        searcher.search(q, collector);
        ScoreDoc[] hits = collector.topDocs().scoreDocs;
        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            results << new Post(title: d.get(LuceneBinding.TITLE_FIELD),
                    journal: d.get(LuceneBinding.JOURNAL_FIELD),
                    poster: d.get(LuceneBinding.POSTER_FIELD),
                    url: d.get(LuceneBinding.URL_FIELD)
                    // todo: citation
            )
        }

        return results
    }


}
