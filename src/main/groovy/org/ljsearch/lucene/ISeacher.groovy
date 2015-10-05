package org.ljsearch.lucene

/**
 * Created by Pavel on 10/5/2015.
 */
interface ISeacher {

    List<Post> search(String journal, String poster, String text)
}