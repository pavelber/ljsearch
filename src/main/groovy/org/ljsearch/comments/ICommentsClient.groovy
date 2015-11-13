package org.ljsearch.comments


/**
 * Created by Pavel on 10/28/2015.
 */
interface ICommentsClient {

    Collection<Comment> getComments(String postUrl)
}