package org.ljsearch.comments

import org.apache.commons.collections.IteratorUtils
import org.htmlcleaner.*
import org.springframework.stereotype.Service
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
 * Created by Pavel on 11/6/2015.
 */
@Service
class CommentsClient implements org.ljsearch.comments.ICommentsClient {
    def static formats = [
            new SimpleDateFormat("MMMMMMM d yyyy, HH:mm:ss Z"),
            new SimpleDateFormat("d MMM, yyyy HH:mm (Z)", Locale.forLanguageTag("ru")),
            new SimpleDateFormat("d MMM, yyyy HH:mm:ss (Z)", Locale.forLanguageTag("ru")),
            new SimpleDateFormat("d MMM, yyyy HH:mm:ss", Locale.forLanguageTag("ru"))
    ]

    def static markup = [
            "//div[@id='container']"                      : [
                    "dates"          : "//abbr/span/text()",
                    "links"          : "//a[@class='permalink']/attribute::href",
                    "comments"       : "//div[contains(concat(' ',@class,' '),' comment-body ')]",
                    "collapsed_links": "//div[contains(concat(' ',@class,' '),' b-leaf-collapsed ')]/div/div/div[2]/ul/li[2]/a/attribute::href | //div[contains(concat(' ',@class,' '),' b-leaf-seemore-width ')]/div/span[1]/a/attribute::href",
                    "usernames"      : "//span[@class='commenter-name']/span/attribute::data-ljuser",
            ],
            "//html[@class='html-schemius html-adaptive']": [
                    "dates"          : '//span[@class="b-leaf-createdtime"]/text()',
                    "links"          : '//a[@class="b-leaf-permalink"]/attribute::href',
                    "comments"       : '//div[@class="b-leaf-article"]',
                    "collapsed_links": "//div[contains(concat(' ',@class,' '),' b-leaf-collapsed ')]/div/div/div[2]/ul/li[2]/a/attribute::href",
                    "usernames"      : "//div[contains(concat(' ',@class,' '),' p-comment ')][@data-full='1']/attribute::data-username",
                    "to_visit"       : "//span[@class='b-leaf-seemore-more']/a/attribute::href"
            ],
            "//div[@align='center']/table[@id='topbox']"  : [
                    "dates"          : "//small/span/text()",
                    "links"          : "//strong/a/attribute::href",
                    "comments"       : "//div[@class='ljcmt_full']/div[2]",
                    "collapsed_links": "//div[starts-with(@id,'ljcmt')][not (@class='ljcmt_full')]/a/attribute::href",
                    "usernames"      : "//div[@class='ljcmt_full']/*//a/b/text()"
            ]]


    static def pattern = Pattern.compile('.*([0-9]+)$')

    @Override
    Collection<Comment> getComments(final String posturl) {

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
                if (aggregate!=null) {
                    comments.putAll(aggregate.dic)
                    loaded.addAll(aggregate.links)
                    unloaded.addAll(aggregate.collapsed_links)
                    unloaded = unloaded - visited
                    unloaded = unloaded - loaded
                }
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


    Document treeFromUrl(String p_url) {
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

    def parseTree(Document doc) {
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


        def dates = getElements(xpath, xp, "dates", doc)
        def links = getElements(xpath, xp, "links", doc)
        def usernames = getElements(xpath, xp, "usernames", doc)
        def collapsed_links = getElements(xpath, xp, "collapsed_links", doc)
        def comments = getElements(xpath, xp, "comments", doc)
        def to_visit = getElements(xpath, xp, "to_visit", doc)
        Map<String, Comment> dic = new HashMap<>();
        if (links.isEmpty()) {
            return
        }

        (0..links.size() - 1).each { i ->
            Matcher m = pattern.matcher(links[i])
            if (m.find()) {
                def cid = m.group()
                if (!dic.containsKey(cid) || !dic[cid].full) {
                    dic[cid] = new Comment(
                            url: links[i],
                            date: parseDate(dates[i]),
                            text: comments[i],
                            poster: usernames[i],
                            full: true)
                }
            }
        }

        for (link in to_visit) {
            collapsed_links.add(link.split('#')[0])
        }

        return new ParsingResult(dic: dic, links: links, collapsed_links: collapsed_links)
    }

    private Date parseDate(String s) {
        for (DateFormat f : formats) {
            try {
                return f.parse(s)
            }
            catch (Exception e) {

            }
        }
        throw new RuntimeException("Upraseable")
    }

    private List getElements(XPath xpath, xp, String markupName, Document doc) {
        def object = xp[markupName]
        if (object == null) {
            return []
        }
        return IteratorUtils.toList(xpath.compile(object).evaluate(doc, XPathConstants.NODESET).iterator()).
                collect {
                    it.textContent
                }
    }


}
