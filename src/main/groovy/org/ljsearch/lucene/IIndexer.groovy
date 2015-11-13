package org.ljsearch.lucene

import org.ljsearch.IndexedType

import javax.annotation.PreDestroy

/**
 * Created by Pavel on 10/5/2015.
 */
interface IIndexer {
    @PreDestroy
    void optimizeAndClose()

    void add(String title, String html, String journal, String poster, String url, Date date, IndexedType type)

    void commit() throws IOException
}
