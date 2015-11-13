package org.ljsearch.comments

import groovy.transform.Canonical

/**
 * Created by Pavel on 10/27/2015.
 */
@Canonical
class Comment {
    String url, text, poster
    Date date
    boolean full

}
