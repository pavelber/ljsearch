package org.ljsearch.lucene

import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.store.RAMDirectory
import org.ljsearch.IndexedType
import spock.lang.Specification

import java.nio.file.Paths


/**
 * Created by Pavel on 10/8/2015.
 */
class IndexerSandbox  {



    public static void main(String[] args) {
        LuceneIndexer indexer = new LuceneIndexer()
        LuceneSearcher seacher = new LuceneSearcher()
        Directory index = FSDirectory.open(Paths.get("c:\\users\\user\\ljsearch"));
        seacher.init(index)
        def res = seacher.search(null, null, "озерет", null, null, IndexedType.Post)
        println res.size()

    }


}