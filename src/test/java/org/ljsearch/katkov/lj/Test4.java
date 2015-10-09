package org.ljsearch.katkov.lj;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopScoreDocCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.ljsearch.lucene.LuceneIndexer;
import org.ljsearch.lucene.QueryHelper;

import java.io.IOException;
import java.util.Date;

public class Test4 {
    public static void main(String[] args) throws Exception {
        Directory index = new RAMDirectory();
        LuceneIndexer indexer = new LuceneIndexer();
        //indexer.setIndexDir("D:\\temp\\l1");
        indexer.init(index);
        indexer.add("titte заголовок", "мы включили apache", "potrebitel_il", "javax_slr", "http://1", new Date(2010, 11, 1));
        indexer.add("сады", "мы гуляли в саду", "tourism_il", "elcy_", "http://1", new Date(2015, 10, 8));
        indexer.optimizeAndClose();

        IndexReader reader = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(reader);
         printResults("сад",null, "potrebitel_il",searcher,null,null);
         printResults( "мы",null,"potrebitel_il",searcher,null,null);
        printResults( "сад",null,"tourism_il",searcher,null,null);
        printResults("мы", null, null,searcher,
                new Date(2010, 1, 1),
                new Date(2061, 1, 1)
        );

        printResults(null, null, "elcy_",searcher,
                new Date(2010, 1, 1),
                new Date(2061, 1, 1)
        );

        /*printResults("мы", null, null,searcher,
                new Date(2010, 1, 1), null
        );
        printResults("мы", null, null,searcher,
                null,
                new Date(2010, 12, 1)
        );*/

    }

    private static void printResults(final String searchWords, String journal, String poster, IndexSearcher searcher, Date datefrom, Date dateto) throws ParseException, IOException {
        System.out.println("Search for words '" + searchWords + "' in journal " + journal);
        Query q = QueryHelper.generate(searchWords, journal, poster, datefrom, dateto);
        TopScoreDocCollector collector = TopScoreDocCollector.create(10);

        searcher.search(q, collector);
        ScoreDoc[] hits = collector.topDocs().scoreDocs;
        System.out.println("Found " + hits.length + " hits.");
        for (int i = 0; i < hits.length; ++i) {
            int docId = hits[i].doc;
            Document d = searcher.doc(docId);
            System.out.println((i + 1) + ". " + d.get("title"));
        }
    }
}
