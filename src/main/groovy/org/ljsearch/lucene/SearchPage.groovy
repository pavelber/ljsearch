package org.ljsearch.lucene

import groovy.transform.CompileStatic

/**
 * One page of search results, newest first.
 * next: opaque cursor for the following page, null when this is the last one.
 * total: number of matching documents.
 * years / posts / comments: only filled when statistics were asked for (first page).
 */
@CompileStatic
class SearchPage {
    int total
    List<Post> results = []
    String next
    Map<Integer, Integer> years
    Integer posts
    Integer comments
}
