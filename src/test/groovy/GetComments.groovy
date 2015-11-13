import org.htmlcleaner.CleanerProperties
import org.htmlcleaner.HtmlCleaner
import org.htmlcleaner.TagNode

def markup = [
        "//div[@id='container'][@class='ng-scope']"   : [
                "dates"          : "//abbr/span/text()",
                "links"          : "//a[@class='permalink']/attribute::href",
                "comments"       : "//div[contains(concat(' ',@class,' '),' comment-body ')]",
                "collapsed_links": "//a[@class='collapsed-comment-link']/attribute::href",
                "usernames"      : "//span[@class='commenter-name']/span/attribute::data-ljuser",
        ],
        "//html[@class='html-schemius html-adaptive']": [
                "dates"          : '//span[@class="b-leaf-createdtime"]/text()',
                "links"          : '//a[@class="b-leaf-permalink"]/attribute::href',
                "comments"       : '//div[@class="b-leaf-article"]',
                "collapsed_links": "//div[contains(concat(' ',@class,' '),' b-leaf-collapsed ')]/div/div/div[2]/ul/li[2]/a/attribute::href",
                "usernames"      : "//div[contains(concat(' ',@class,' '),' p-comment ')][@data-full='1']/attribute::data-username",
        ],
        "//div[@align='center']/table[@id='topbox']"  : [
                "dates"          : "//small/span/text()",
                "links"          : "//strong/a/attribute::href",
                "comments"       : "//div[@class='ljcmt_full']/div[2]",
                "collapsed_links": "//div/a/attribute::href",
                //"collapsed_links": "//div[starts-with(@id,'ljcmt')][not (@class='ljcmt_full')]/a/attribute::href",
                "usernames"      : "//div[@class='ljcmt_full']/*//a/b/text()"
        ]]

//def url = "http://rabota-il.livejournal.com/9069326.html"
def url = "http://rusisrael.livejournal.com/7642532.html"
url += "?nojs=1"
/*
Document doc = Jsoup.connect(url).get();
if (doc.title().contains("LiveJournal Bot Policy")) {
    throw new RuntimeException("LiveJournal Bot Policy")
}
*/
HtmlCleaner cleaner = new HtmlCleaner();

// take default cleaner properties
CleanerProperties props = cleaner.getProperties();

// customize cleaner's behaviour with property setters
//props.setXXX(...);

// Clean HTML taken from simple string, file, URL, input stream,
// input source or reader. Result is root node of created
// tree-like structure. Single cleaner instance may be safely used
// multiple times.
TagNode node = cleaner.clean(new URL(url));

def xp
Object[] myNodes
markup.each { k, v ->
    myNodes = node.evaluateXPath(k)
    if (myNodes.length > 0) {
        xp = v
    }
}
for (Object n : myNodes) {
    def dates = n.evaluateXPath(xp["dates"])
    def links = n.evaluateXPath(xp["links"])
    def usernames = n.evaluateXPath(xp["usernames"])
    def collapsed_links = n.evaluateXPath(xp["collapsed_links"])
    def comments = n.evaluateXPath(xp["comments"])

    println "got ${dates.length} dates"
    println "got ${links.length} links"
    println "got ${usernames.length} usernames"
    println "got ${collapsed_links.length} collapsed_links"
    println "got ${comments.length} comments"

}

