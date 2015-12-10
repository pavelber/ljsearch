package org.ljsearch.lucene

import org.ljsearch.IndexedType

/**
 * Created by Pavel on 10/5/2015.
 */
interface ISearcher {

    Set<Post> search(String journal, String poster, String text, Date from, Date t, IndexedType type)
}