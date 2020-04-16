package test


import org.ljsearch.comments.CommentsClient

/**
 * Created by Pavel on 12/4/2015.
 */
class RunClient {
    static void main(String[] args) {
        CommentsClient client = new CommentsClient()

        def journsls = [
                "https://krimsky.livejournal.com/7910.html",
                "https://krimsky.livejournal.com/7644.html",
                "https://krimsky.livejournal.com/15751.html",
                "https://krimsky.livejournal.com/16084.html",
                "https://krimsky.livejournal.com/655519.html",
                "https://krimsky.livejournal.com/839.html",
                "https://ladies-il.livejournal.com/7427288.html",
                "https://jinuaria.livejournal.com/138735.html",
                "https://lapsha-ru-il.livejournal.com/56132.html",
                "https://lenay.livejournal.com/1694884.html",
                "https://gava.livejournal.com/235053.html",
                "https://help-in-home.livejournal.com/278286.html",
                "https://javax-slr.livejournal.com/783423.html",
                "https://evg25.livejournal.com/201495.html",
                "https://inetshop-il.livejournal.com/7171401.html",
                "https://henic.livejournal.com/295192.html",
                "https://edik-m.livejournal.com/63526.html",
                "https://detishki-israel.livejournal.com/8942165.html",
                "https://bambik.livejournal.com/2802534.html",
                "https://komp-online-il.livejournal.com/2239792.html",
        ]

        journsls.each { j ->
            println(j)
            downloadJournal(client, j)
            //          downloadJournal(client, j)
            //comments.each { c -> println("${c.subject}  --- ${c.text}") }
        }
    }

    private static void downloadJournal(CommentsClient client, String j) {
        long t = System.currentTimeMillis()
        def comments = client.getComments(j)
        long t1 = System.currentTimeMillis()
        def nullOrEmpty = comments.findAll { c -> c == null || c.isEmpty() }.size()
        println(comments.size() + " " + (t1 - t) + " null:" + nullOrEmpty)
    }
}
