package org.ljsearch.katkov.lj;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
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

public class Test2 {
    public static void main(String[] args) throws Exception {
        IndexReader reader =  DirectoryReader.open(FSDirectory.open(Paths.get("D:\\temp\\ljsearch\\")));
        IndexSearcher searcher = new IndexSearcher(reader);

        printResults( "время",searcher);
        /*printResults( "Синай",searcher);
        printResults( "хушот",searcher);
        printResults( "ночёвка",searcher);
        printResults( "Англия",searcher);
        printResults( "Бейтар",searcher);
        printResults( "пятница",searcher);
*/
    }

    private static void printResults(final String searchWords, IndexSearcher searcher) throws ParseException, IOException {
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
