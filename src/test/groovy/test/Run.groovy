package test

import org.apache.commons.collections.IteratorUtils
import org.htmlcleaner.*
import org.ljsearch.comments.Comment
import org.ljsearch.comments.ParsingResult
import org.w3c.dom.Document

import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpression
import javax.xml.xpath.XPathFactory
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Created by Pavel on 10/27/2015.
 */

class Run {



    def static markup = [
            "//div[@id='container']"                      : [
                    'blocks'   : '//div[contains(concat(" ",@class," ")," comment ")]',
                    "link"     : ".//a[@class='permalink']/attribute::href",
                    "date"     : ".//abbr/span/text()",
                    "text"     : ".//div[contains(concat(' ',@class,' '),' comment-body ')]//text()",
                    "user"     : ".//span[@class='commenter-name']/span/attribute::data-ljuser",
                    "subject"  : ".//div[@class='comment-subject']/text()",
                    "collapsed": "//a[@class='collapsed-comment-link']/attribute::href",
            ],
            "//html[@class='html-schemius html-adaptive']": [
                    'blocks'   : '//div[contains(concat(" ",@class," ")," comment ")' +
                            'and not(contains(concat(" ",@class," ")," b-leaf-collapsed "))]',
                    'link'     : './/a[@class="b-leaf-permalink"]/attribute::href',
                    'date'     : './/span[@class="b-leaf-createdtime"]/text()',
                    'text'     : './/div[@class="b-leaf-article"]//text()',
                    'user'     : './/span[@class="b-leaf-username-name"]//text()',
                    'subject'  : './/h4[@class="b-leaf-subject"]//text()',
                    "collapsed": "//div[contains(concat(' ',@class,' '),' b-leaf-collapsed ')]" +
                            "/div/div/div[2]/ul/li[2]/a/attribute::href",
                    "to_visit" : "//span[@class='b-leaf-seemore-more']/a/attribute::href",
            ],
            "//div[@align='center']/table[@id='topbox']"  : [
                    "blocks"   : "//div[@class='ljcmt_full']",
                    "link"     : ".//strong/a/attribute::href",
                    "date"     : ".//small/span/text()",
                    "text"     : "./div[2]//text()",
                    "user"     : ".//td/span/a/b/text()",
                    "subject"  : ".//td/h3/text()",
                    "collapsed": "//div[starts-with(@id,'ljcmt')][not(@class='ljcmt_full')]/a/attribute::href",
            ]]


    static def pattern = Pattern.compile('.*([0-9]+)$')

    static def parseComments(String posturl) {
        Map<String, Comment> comments = [:]

        def visited = new HashSet<>()
        def loaded = new HashSet<>()
        def unloaded = new Stack<String>()
        unloaded.push(posturl)
        int c_len_old = 0
        int page = 2

        while (true) {
            while (unloaded.size() > 0) {
                def url = unloaded.pop()
                def doc = treeFromUrl(url)
                visited.add(url)
                def aggregate = parseTree(doc)
                comments.putAll(aggregate.dic)
                loaded.addAll(aggregate.links)
                unloaded.addAll(aggregate.collapsed_links)
                unloaded = unloaded - visited
                unloaded = unloaded - loaded
                println "${comments.size()} ${loaded.size()} ${unloaded.size()}"
            }

            int c_len = comments.size()
            if (c_len == c_len_old) {
                break
            }
            c_len_old = c_len
            unloaded.add("$posturl?page=$page")
            page++


        }
        return comments
    }


    static Document treeFromUrl(String p_url) {
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
        return new DomSerializer(props).createDOM(node);
    }

    static def parseTree(Document doc) {
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

        def blocks = getElements(xpath, xp, "blocks", doc)
        def collapsed = getStringElements(xpath, xp, 'collapsed', doc)
        def to_visit = getStringElements(xpath, xp, "to_visit", doc)
        def fields = ['link', 'date', 'text', 'user', 'subject']
        def comments = [:]
        def links = []
        for (block in blocks) {
            def comment = new Comment()
            for (f in fields) {
                comment[f] = getStringElements(xpath, xp, f, block).join(" ").trim()
            }
            Matcher m = pattern.matcher(comment['link'])
            if (m.find()) {
                def cid = m.group()
                comments[cid] = comment
            } else {
                throw new RuntimeException()
            }

            links.add(comment.link)


        }


        for (link in to_visit) {
            collapsed.add(link.split('#')[0])
        }

        return new ParsingResult(dic: comments, links: links, collapsed_links: collapsed)
    }



    private static List getStringElements(XPath xpath, xp, String markupName, Object doc) {
        return getElements(xpath, xp, markupName,doc).
                collect {
                    it.textContent
                }
    }

    private static List getElements(XPath xpath, xp, String markupName, Object doc) {
        def object = xp[markupName]
        if (object == null) {
            return []
        }
        return IteratorUtils.toList(xpath.compile(object).evaluate(doc, XPathConstants.NODESET).iterator())
    }

    public static void main(String[] args) {

        // def url = "http://rusisrael.livejournal.com/7642532.html"
        //println parseComments("http://rabota-il.livejournal.com/9069326.html").size()  // good
        //println parseComments("http://potrebitel-il.livejournal.com/22412050.html") // good
        //println parseComments("http://potrebitel-il.livejournal.com/22412050.html").size() // good
        //println parseComments("http://rusisrael.livejournal.com/283608.html").size() // 77 instead of 81
        println parseComments("http://rusisrael.livejournal.com/7635825.html").size()
        println parseComments("http://dolboeb.livejournal.com/2868126.html").size() // pages

    }

}
