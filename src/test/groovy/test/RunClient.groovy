package test

import org.ljsearch.comments.Comment
import org.ljsearch.comments.CommentsClient

/**
 * Created by Pavel on 12/4/2015.
 */
class RunClient {
    public static void main(String[] args) {
        CommentsClient client = new CommentsClient()
        def comments = client.getComments("http://spamsink.livejournal.com/336909.html")
    }
}
