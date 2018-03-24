package test

import org.ljsearch.comments.Comment
import org.ljsearch.comments.CommentsClient

/**
 * Created by Pavel on 12/4/2015.
 */
class RunClient {
     static void main(String[] args) {
        CommentsClient client = new CommentsClient()
        def comments = client.getComments("https://komp-online-il.livejournal.com/2239792.html")
        comments.each { c -> println("${c.subject}  --- ${c.text}")}
    }
}
