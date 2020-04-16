package org.ljsearch.comments

import org.apache.commons.collections.IteratorUtils
import org.htmlcleaner.CleanerProperties
import org.htmlcleaner.DomSerializer
import org.htmlcleaner.HtmlCleaner
import org.htmlcleaner.TagNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.w3c.dom.Document

import javax.xml.xpath.XPath
import javax.xml.xpath.XPathConstants
import javax.xml.xpath.XPathExpression
import javax.xml.xpath.XPathFactory
import java.util.regex.Matcher
import java.util.regex.Pattern

/**
 * Created by Pavel on 11/6/2015.
 */
@Service
class CommentsClient implements ICommentsClient {

    private static Logger logger = LoggerFactory.getLogger(CommentsClient.class)


    def static markups = [
            [
                    'blocks'   : '//div[contains(concat(" ",@class," ")," comment ")]',
                    "link"     : ".//a[@class='permalink']/attribute::href",
                    "date"     : ".//abbr/span/text()",
                    "text"     : ".//div[contains(concat(' ',@class,' '),' comment-body ')]//text()",
                    "user"     : ".//span[@class='commenter-name']/span/attribute::data-ljuser",
                    "subject"  : ".//div[@class='comment-subject']/text()",
                    "collapsed": "//a[@class='collapsed-comment-link']/attribute::href",
            ],
            [
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
            [
                    "blocks"   : "//div[@class='ljcmt_full']",
                    "link"     : ".//td[@class='social-links']/p/strong/a/attribute::href",
                    "date"     : ".//small/span/text()",
                    "text"     : "./div[2]//text()",
                    "user"     : ".//td/span/a/b/text()",
                    "subject"  : ".//td/h3/text()",
                    "collapsed": "//div[starts-with(@id,'ljcmt')][not(@class='ljcmt_full')]/a/attribute::href",
            ],
            [
                    "blocks"   : '//div[starts-with(@id, "ljcmt")]',
                    "link"     : ".//div[contains(@style, 'smaller')]/a[last()]/attribute::href",
                    "date"     : ".//tr/td/span/text()",
                    "text"     : "./div[2]//text()",
                    "user"     : ".//td/span/a/b/text()",
                    "subject"  : ".//td/h3/text()",
                    "collapsed": "//div[starts-with(@id,'ljcmt')][not(@class='ljcmt_full')]/a/attribute::href",
            ],
            [
                    "blocks"   : "//div[@class='ljcmt_full']",
                    "link"     : ".//div[@class='commentLinkbar']/ul/li[last()-1]/a/attribute::href",
                    "date"     : ".//div[@class='commentHeader']/span[1]/text()",
                    "text"     : ".//div[contains(concat(' ',@class,' '),' commentText ')]//text()",
                    "user"     : ".//span[@class='ljuser']/span/attribute::data-ljuser",
                    "subject"  : ".//span[@class='commentHeaderSubject']/text()",
                    "collapsed": "//div[@class='commentHolder']/div[@class='commentText']/a/attribute::href",
            ],
            [
                    "blocks"   : '//div[starts-with(@id, "ljcmt")]',
                    "link"     : ".//span[@class='comment-datetimelink']/a[last()]/attribute::href",
                    "date"     : ".//span[@class='comment-datetimelink']/a/span/text()",
                    "text"     : "./div[2]//text()",
                    "user"     : ".//div[@class='comment-poster-info']/span/attribute::data-ljuser",
                    "subject"  : ".//div[contains(concat(' ',@class,' '),' comment-head-in ')]/h3/text()",
                    "collapsed": "//div[starts-with(@id,'ljcmt')][not(@class='ljcmt_full')]/a/attribute::href",
            ]]

    def static markup_guess = [
            "//div[@id='container']",
            "//html[@class='html-schemius html-adaptive']",
            "//div[@align='center']/table[@id='topbox']",
            "//table[contains(@class, 'standard')]",
            "//div[@class='bodyblock']",
            "//html[contains(@class, 'html-s2-no-adaptive')]",
    ]

    static def pattern = Pattern.compile('.*([0-9]+)$')


    @Override
    Collection<Comment> getComments(String postUrl) {
        def comments = [] as List<Comment>
        for (int i = 0; i < CommentsClient.markups.size(); i++) {

            def tempComments = getComments(postUrl, i)
            if (tempComments != null && tempComments.size() > comments.size()) {
                comments = tempComments
            }
        }

        logger.info(postUrl + " - " + comments.size() + " comments")

        return comments
    }

    static Collection<Comment> getComments(final String posturl, int markupIndex) {

        Map<String, Comment> comments = [:]
        def visited = new HashSet<>()
        def loaded = new HashSet<>()
        Stack<String> unloaded = new Stack<String>()
        unloaded.push(posturl)
        int c_len_old = 0
        int page = 2

        while (true) {
            while (unloaded.size() > 0) {
                def url = unloaded.pop()
                def doc
                try {
                    doc = treeFromUrl(url)
                } catch (Exception w) {
                    break
                }
                visited.add(url)
                def aggregate = parseTree(doc, posturl, markupIndex)// Try another markup
                if (aggregate == null) return null
                comments.putAll(aggregate.dic)
                loaded.addAll(aggregate.links)
                unloaded.addAll(aggregate.collapsed_links)
                unloaded = unloaded - visited
                unloaded = unloaded - loaded
            }

            int c_len = comments.size()
            if (c_len == c_len_old) {
                break
            }
            c_len_old = c_len
            unloaded.add("$posturl?page=$page")
            page++


        }
        return comments.values()
    }


    static Document treeFromUrl(String p_url) {
        String url = p_url.split("#")[0]
        if (!url.contains("?")) {
            url += "?nojs=1"
        } else {
            url = url[0..url.indexOf("?")] + "nojs=1&" + url[url.indexOf("?") + 1..url.length() - 1]
        }

        HtmlCleaner cleaner = new HtmlCleaner()

        CleanerProperties props = cleaner.getProperties()

        props.setTranslateSpecialEntities(true)
        props.setTransResCharsToNCR(true)
        props.setOmitComments(true)
        props.setAllowHtmlInsideAttributes(true)
        props.setAllowMultiWordAttributes(true)
        props.setRecognizeUnicodeChars(true)

        TagNode node = cleaner.clean(new URL(url))
        //assert page.status_code == 200
        //assert "<title>LiveJournal Bot Policy</title>" not in page.text
        return new DomSerializer(props).createDOM(node)
    }

    static def parseTree(Document doc, String posturl, int markupIndex = -1) {

        if (markupIndex > markups.size())
            throw new RuntimeException()
        XPathFactory xPathfactory = XPathFactory.newInstance()
        XPath xpath = xPathfactory.newXPath()

        def xp
        def myNodes
        if (markupIndex > -1) {
            xp = markups[markupIndex]
        } else {
            markup_guess.eachWithIndex { k, i ->
                XPathExpression expr = xpath.compile(k)
                myNodes = expr.evaluate(doc, XPathConstants.NODESET)
                if (myNodes.length > 0) {
                    xp = markups[i]
                }
            }
        }


        def comments = [:]
        def links = []
        if (xp == null) {
            //logger.warn("null block on!")
            return null // Try another markup
        }
        def blocks = getElements(xpath, xp, "blocks", doc)
        def collapsed = getStringElements(xpath, xp, 'collapsed', doc)
        def to_visit = getStringElements(xpath, xp, "to_visit", doc)
        def fields = ['link', 'date', 'text', 'user', 'subject']

        for (block in blocks) {
            def comment = new Comment()
            for (f in fields) {
                comment[f] = getStringElements(xpath, xp, f, block).join(" ").trim()
            }
            if (!comment.isEmpty()) {
                Matcher m = pattern.matcher(comment.link)
                if (m.find()) {
                    def cid = m.group()
                    comments[cid] = comment
                } else {
                    return null// Try another markup
                }

                links.add(comment.link)
            } else {
                // logger.warn("Empty comment at $posturl at ${comment.date}")
                return null // Try another markup
            }

        }


        for (link in to_visit) {
            collapsed.add(link.split('#')[0])
        }

        return new ParsingResult(dic: comments, links: links, collapsed_links: collapsed)
    }


    private static List getStringElements(XPath xpath, xp, String markupName, Object doc) {
        return getElements(xpath, xp, markupName, doc).
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


}
