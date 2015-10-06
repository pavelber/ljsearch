package org.ljsearch.katkov.lj;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.sandbox.queries.DuplicateFilter;
import org.apache.lucene.search.*;
import org.apache.lucene.store.FSDirectory;
import org.ljsearch.lucene.LuceneBinding;
import org.ljsearch.lucene.LuceneIndexer;
import org.ljsearch.lucene.QueryHelper;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Date;

public class Test3 {
    public static void main(String[] args) throws Exception {
       /* LuceneIndexer indexer = new LuceneIndexer();
        indexer.setIndexDir("D:\\temp\\lucene1");
        indexer.init();
        indexer.add("titte заголовок","мы включили apache","potrebitel_il","javax_slr","http://1", new Date());
        indexer.add("сады","мы гуляли в саду","tourism_il","elcy_","http://1", new Date());
        indexer.commit();*/
      //  indexer.optimizeAndClose();
        IndexReader reader =  DirectoryReader.open(FSDirectory.open(Paths.get("D:\\temp\\lucene1")));
        IndexSearcher searcher = new IndexSearcher(reader);

        printResults("сад", "potrebitel_il",searcher);
        printResults( "мы","potrebitel_il",searcher);
        printResults( "сад","tourism_il",searcher);

    }

    private static void printResults( final String searchWords, String journal, IndexSearcher searcher) throws ParseException, IOException {
        System.out.println("Search for words '"+searchWords+"' in journal "+journal);
        Query q = QueryHelper.generate(searchWords,journal,null);
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
