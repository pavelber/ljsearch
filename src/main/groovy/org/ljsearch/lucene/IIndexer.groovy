package org.ljsearch.lucene;

import javax.annotation.PreDestroy;
import java.io.IOException;

/**
 * Created by Pavel on 10/5/2015.
 */
interface IIndexer {
    @PreDestroy
    void optimizeAndClose()

    void add(String title, String html, String journal, String poster, String url, Date date)

    void commit() throws IOException
}
