package test

import org.apache.commons.collections.IteratorUtils
import org.htmlcleaner.*
import org.ljsearch.comments.Comment
import org.w3c.dom.Document

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpression
import javax.xml.xpath.XPathFactory
import java.text.SimpleDateFormat
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Created by Pavel on 10/27/2015.
 */
class RunOld {

    def static format = new SimpleDateFormat("MMMMMMM d yyyy, HH:mm:ss Z")

    def static markup = [
            "//div[@id='container']"   : [
                    "dates"          : "//abbr/span/text()",
                    "links"          : "//a[@class='permalink']/attribute::href",
                    "comments"       : "//div[contains(concat(' ',@class,' '),' comment-body ')]",
                    "collapsed_links" : "//div[contains(concat(' ',@class,' '),' b-leaf-collapsed ')]/div/div/div[2]/ul/li[2]/a/attribute::href | //div[contains(concat(' ',@class,' '),' b-leaf-seemore-width ')]/div/span[1]/a/attribute::href",
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
                    "collapsed_links": "//div[starts-with(@id,'ljcmt')][not (@class='ljcmt_full')]/a/attribute::href",
                    "usernames"      : "//div[@class='ljcmt_full']/*//a/b/text()"
            ]]


    static def pattern = Pattern.compile('.*([0-9]+)$')

    static def parseComments(String url) {
        Map<String,Comment> comments = [:]
        def nxt = url
        def prev = ""
        def visited = new HashSet<>()

        while (true) {
            getFromUrl(nxt, comments, visited)
            prev = nxt
            nxt = firstUnloaded(comments,visited)
            if (prev == nxt) {
                throw new RuntimeException("Stuck")
            }
            if (nxt == null) {
                break
            }
            visited.clear()
        }
        return comments

    }

    static def firstUnloaded(Map<String,Comment> dic, Set<String> visited) {
        for (String c : dic.keySet()) {
            if (!dic[c].full) {
                if (!visited.contains(dic[c].url))
                return dic[c].url
            }
        }
        return null
    }

    static def getFromUrl(String p_url, Map<String,Comment> dic, Set<String> visited) {
        String url = p_url.split("#")[0]
        if (!url.contains("?")) {
            url += "?nojs=1"
        } else {
            url = url[0..url.indexOf("?")] + "nojs=1&" + url[url.indexOf("?") + 1..url.length() - 1]
        }

        HtmlCleaner cleaner = new HtmlCleaner();

        CleanerProperties props = cleaner.getProperties();

        props.setTranslateSpecialEntities(true);
        props.setTransResCharsToNCR(true);
        props.setOmitComments(true);
        props.setAllowHtmlInsideAttributes(true);
        props.setAllowMultiWordAttributes(true);
        props.setRecognizeUnicodeChars(true);

        TagNode node = cleaner.clean(new URL(url));
        //assert page.status_code == 200
        //assert "<title>LiveJournal Bot Policy</title>" not in page.text
        String str = new SimpleXmlSerializer(cleaner.getProperties()).getXmlAsString(node);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = new DomSerializer(props).createDOM(node);

        XPathFactory xPathfactory = XPathFactory.newInstance();
        XPath xpath = xPathfactory.newXPath();

        def xp
        def myNodes

        markup.each { k, v ->
            XPathExpression expr = xpath.compile(k);
            myNodes = expr.evaluate(doc, XPathConstants.NODESET);
            if (myNodes.length > 0) {
                xp = v
            }
        }

        def dates = IteratorUtils.toList(xpath.compile(xp["dates"]).evaluate(doc, XPathConstants.NODESET).iterator())
        def links = IteratorUtils.toList(xpath.compile(xp["links"]).evaluate(doc, XPathConstants.NODESET).iterator())
        def usernames = IteratorUtils.toList(xpath.compile(xp["usernames"]).evaluate(doc, XPathConstants.NODESET).iterator())
        def collapsed_links = IteratorUtils.toList(xpath.compile(xp["collapsed_links"]).evaluate(doc, XPathConstants.NODESET).iterator())
        def comments = IteratorUtils.toList(xpath.compile(xp["comments"]).evaluate(doc, XPathConstants.NODESET).iterator())
        //assert all([len(l) == len(dates) for l in [links,usernames,comments]])

        if (links.isEmpty()){
            return
        }

        (0..links.size() - 1).each { i ->
            Matcher m = pattern.matcher(links[i].textContent)
            if (m.find()) {

                def cid = m.group()
                if (!dic.containsKey(cid) || !dic[cid].full) {
                    dic[cid] = new Comment(
                            url: links[i].textContent,
                            date: format.parse(dates[i].textContent),
                            text: comments[i].textContent,
                            poster: usernames[i].textContent,
                            full: true)
                    links.add(p_url)
                    visited.addAll(links)
                }
            }
        }

        for (link in collapsed_links) {
            Matcher m = pattern.matcher(link.value)
            if (m.find()) {

                def cid = m.group()

                if (!dic.containsKey(cid)) {
                    dic[cid] = new Comment(
                            url: link.textContent,
                            full: false)
                }
            }

        }
    }

    public static void main(String[] args) {

        // def url = "http://rusisrael.livejournal.com/7642532.html"
        //println parseComments("http://rabota-il.livejournal.com/9069326.html").size()  // good
        //println parseComments("http://potrebitel-il.livejournal.com/22412050.html").size() // nothing
        println parseComments("http://rusisrael.livejournal.com/283608.html").size() // 77 instead of 88
        //println parseComments("http://rusisrael.livejournal.com/7635825.html").size()
        //println parseComments("http://dolboeb.livejournal.com/2868126.html").size() // pages

    }

}
