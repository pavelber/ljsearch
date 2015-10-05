package org.ljsearch.katkov.lj;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.FSDirectory;
import org.ljsearch.lucene.LuceneBinding;
import org.ljsearch.lucene.LuceneIndexer;
import org.ljsearch.lucene.QueryHelper;

import java.io.IOException;
import java.nio.file.Paths;

public class Test3 {
    public static void main(String[] args) throws Exception {
        LuceneIndexer indexer = new LuceneIndexer();
        indexer.setIndexDir("D:\\temp");
        indexer.init();
        indexer.add("titte заголовок","мы включили apache","potrebitel_il","javax_slr");
        indexer.add("сады","мы гуляли в саду","tourism_il","elcy_");
      //  indexer.optimizeAndClose();
        IndexReader reader =  DirectoryReader.open(FSDirectory.open(Paths.get("D:\\temp")));
        IndexSearcher searcher = new IndexSearcher(reader);

        printResults(LuceneBinding.TITLE_FIELD, "сад",searcher);
        printResults(LuceneBinding.RUS_TITLE_FIELD, "сад",searcher);
        printResults(LuceneBinding.CONTENT_FIELD, "мы",searcher);
        printResults(LuceneBinding.CONTENT_FIELD, "сад",searcher);
        printResults(LuceneBinding.RUS_CONTENT_FIELD, "сад",searcher);
        printResults(LuceneBinding.CONTENT_FIELD, "apaches",searcher);
        printResults(LuceneBinding.ENG_CONTENT_FIELD, "apaches",searcher);

    }

    private static void printResults(final String searchField, final String searchWords, IndexSearcher searcher) throws ParseException, IOException {
        System.out.println("Search for words '"+searchWords+"' in field "+searchField);
        Query q = QueryHelper.generate(searchWords);
        TopScoreDocCollector collector = TopScoreDocCollector.create(10);
        searcher.search(q, collector);
        ScoreDoc[] hits = collector.topDocs().scoreDocs;
        System.out.println("Found " + hits.length + " hits.");
        for(int i=0;i<hits.length;++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            System.out.println((i + 1) + ". " + d.get("title"));
        }
    }
}
