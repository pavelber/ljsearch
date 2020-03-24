package test


import org.ljsearch.comments.CommentsClient

/**
 * Created by Pavel on 12/4/2015.
 */
class RunClient {
    static void main(String[] args) {
        CommentsClient client = new CommentsClient()
        def journsls = ["http://krimsky.livejournal.com/655519.html",
                        "http://ladies-il.livejournal.com/7427288.html",
                        "http://jinuaria.livejournal.com/138735.html",
                        "http://lapsha-ru-il.livejournal.com/56132.html",
                        "http://lenay.livejournal.com/1694884.html",
                        "http://gava.livejournal.com/235053.html",
                        "http://help-in-home.livejournal.com/278286.html",
                        "http://javax-slr.livejournal.com/783423.html",
                        "http://evg25.livejournal.com/201495.html",
                        "http://inetshop-il.livejournal.com/7171401.html",
                        "http://henic.livejournal.com/295192.html",
                        "http://edik-m.livejournal.com/63526.html",
                        "http://detishki-israel.livejournal.com/8942165.html",
                        "http://bambik.livejournal.com/2802534.html",
                        "http://komp-online-il.livejournal.com/2239792.html"]

        journsls.each { j->
            def comments = client.getComments(j)
            comments.each { c -> println("${c.subject}  --- ${c.text}") }
        }
    }
}
