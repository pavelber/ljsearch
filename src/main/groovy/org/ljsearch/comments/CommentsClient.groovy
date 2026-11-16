package org.ljsearch.comments

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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


    // Without these a stalled LiveJournal page blocks the single downloader
    // thread forever (same failure as the getevents hang of 2026-09-20).
    static final int CONNECT_TIMEOUT_MS = 30 * 1000
    static final int READ_TIMEOUT_MS = 60 * 1000

    // How many bytes of raw HTML of one post are kept while the markups are
    // tried. The cache holds the downloaded bytes, not parsed DOM trees: a DOM
    // is many times bigger than its HTML, and 16 cached DOMs of a big thread
    // caused the heap OOMs of 2026-11-07..16. Each markup re-parses the bytes.
    static final long PAGE_CACHE_MAX_BYTES = 8L * 1024 * 1024

    @Override
    Collection<Comment> getComments(String postUrl) {
        // Every strategy walks the same pages; share the downloads between them so
        // that each page is requested from LiveJournal once, not once per markup.
        PageCache cache = new PageCache()

        // Current LJ pages carry the comments as JSON in Site.page; the HTML
        // markups below match nothing there. When the JSON is present it is the
        // answer (also when it has no comments), and no DOM is built at all.
        Collection<Comment> fromJson = getJsonComments(postUrl, cache)
        if (fromJson != null) {
            logger.info(postUrl + " - " + fromJson.size() + " comments")
            return fromJson
        }

        def comments = [] as List<Comment>
        for (int i = 0; i < CommentsClient.markups.size(); i++) {

            def tempComments = getComments(postUrl, i, cache)
            if (tempComments != null && tempComments.size() > comments.size()) {
                comments = tempComments
            }
        }

        logger.info(postUrl + " - " + comments.size() + " comments")

        return comments
    }

    /**
     * For the comments back-fill: like getComments, but a failure is an
     * exception instead of an empty list. The post page itself must load
     * (FileNotFoundException for a deleted post, IOException for network
     * trouble) and must carry the Site.page JSON (NoCommentsJsonException
     * otherwise, e.g. a Bot Policy page).
     */
    Collection<Comment> getCommentsOrFail(String postUrl) {
        PageCache cache = new PageCache()
        byte[] first = cache.getHtml(postUrl)
        if (parseJson(first) == null) {
            throw new NoCommentsJsonException(postUrl)
        }
        Collection<Comment> comments = getJsonComments(postUrl, cache)
        logger.info(postUrl + " - " + comments.size() + " comments (back-fill)")
        return comments
    }

    static class NoCommentsJsonException extends Exception {
        NoCommentsJsonException(String url) {
            super("No Site.page comments JSON in " + url)
        }
    }

    static Collection<Comment> getComments(final String posturl, int markupIndex) {
        return getComments(posturl, markupIndex, new PageCache())
    }

    static Collection<Comment> getComments(final String posturl, int markupIndex, PageCache cache) {
        return walkPages(posturl, cache) { String url ->
            parseTree(cache.get(url), posturl, markupIndex)
        }
    }

    /**
     * Comments from the Site.page JSON of the post pages, or null when the
     * first page has no such JSON (an old-style page for the markups).
     */
    static Collection<Comment> getJsonComments(final String posturl, PageCache cache) {
        return walkPages(posturl, cache) { String url ->
            ParsingResult result = parseJson(cache.getHtml(url))
            if (result == null && url != posturl) {
                // only the post page itself decides the format
                result = new ParsingResult(dic: [:], links: [], collapsed_links: [])
            }
            result
        }
    }

    private static final ObjectMapper JSON = new ObjectMapper()
    private static final String SITE_PAGE = "Site.page = "

    /**
     * Comments of one page from its "Site.page = {...}" script. Returns null
     * when the page has no Site.page with a comments list. Comments of a
     * collapsed thread are not in the JSON (loaded = 0); their thread URLs are
     * returned as collapsed_links, and that page has the thread expanded.
     */
    static ParsingResult parseJson(byte[] html) {
        String page = new String(html, "UTF-8")
        int start = page.indexOf(SITE_PAGE)
        if (start < 0) {
            return null
        }
        JsonNode site
        JsonParser parser = JSON.getFactory().createParser(new StringReader(page.substring(start + SITE_PAGE.length())))
        try {
            site = JSON.readTree(parser) // reads the object, ignores the script after it
        } finally {
            parser.close()
        }
        JsonNode list = site?.get("comments")
        if (list == null || !list.isArray()) {
            return null
        }

        Map<String, Comment> comments = [:]
        List<String> links = []
        List<String> collapsed = []
        for (JsonNode c : list) {
            String threadUrl = text(c, "thread_url")
            String thread = text(c, "thread") ?: text(c, "dtalkid")
            if (threadUrl.isEmpty() || thread.isEmpty()) {
                continue
            }
            String url = threadUrl.split("#")[0]
            if (c.path("deleted").asInt(0) != 0) {
                continue
            }
            if (c.path("loaded").asInt(1) == 0) {
                collapsed.add(url)
                continue
            }
            Comment comment = new Comment()
            comment.link = threadUrl
            comment.user = text(c, "uname") ?: text(c, "dname")
            comment.subject = htmlToText(text(c, "subject"))
            comment.text = htmlToText(text(c, "article"))
            long ts = c.path("ctime_ts").asLong(0)
            if (ts > 0) {
                comment.@date = new Date(ts * 1000L)
            } else {
                comment.date = text(c, "ctime")
            }
            if (comment.isEmpty()) {
                continue
            }
            comments[thread] = comment
            links.add(url)
        }
        return new ParsingResult(dic: comments, links: links, collapsed_links: collapsed)
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field)
        return value == null || value.isNull() ? "" : value.asText()
    }

    /** Comment HTML to plain text: line breaks kept, tags dropped, entities decoded. */
    static String htmlToText(String html) {
        if (html == null || html.isEmpty()) {
            return ""
        }
        String withBreaks = html.replaceAll(/(?i)<br\s*\/?>|<\/p>|<\/div>/, "\n")
        // numeric entities; HtmlCleaner decodes only the named ones
        withBreaks = withBreaks.replaceAll(/&#(x[0-9a-fA-F]+|[0-9]+);/) { all, String code ->
            int cp = code.startsWith("x") ? Integer.parseInt(code.substring(1), 16) : Integer.parseInt(code)
            Character.isValidCodePoint(cp) && cp != 38 && cp != 60 ? new String(Character.toChars(cp)) : all
        }
        CleanerProperties props = new CleanerProperties()
        props.setTranslateSpecialEntities(true)
        props.setRecognizeUnicodeChars(true)
        props.setDeserializeEntities(true)
        props.setOmitComments(true)
        TagNode node = new HtmlCleaner(props).clean(withBreaks)
        return node.getText().toString().trim()
    }

    /**
     * Loads the post page, the collapsed threads and ?page=N pages until no new
     * comments appear. parser returns null when the page is not in its format;
     * then the whole result is null. A page that fails to load ends the walk.
     */
    private static Collection<Comment> walkPages(final String posturl, PageCache cache, Closure<ParsingResult> parser) {

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
                ParsingResult aggregate
                try {
                    aggregate = parser.call(url)
                } catch (Exception w) {
                    break
                }
                visited.add(url)
                if (aggregate == null) return null // Try another markup
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
        return parseHtml(downloadHtml(p_url))
    }

    /** One GET of the comments page, with timeouts. Returns the raw bytes. */
    static byte[] downloadHtml(String p_url) {
        String url = p_url.split("#")[0]
        if (!url.contains("?")) {
            url += "?nojs=1"
        } else {
            url = url[0..url.indexOf("?")] + "nojs=1&" + url[url.indexOf("?") + 1..url.length() - 1]
        }

        URLConnection connection = new URL(url).openConnection()
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS)
        connection.setReadTimeout(READ_TIMEOUT_MS)
        InputStream input = connection.getInputStream()
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream(64 * 1024)
            byte[] buffer = new byte[16 * 1024]
            int n
            while ((n = input.read(buffer)) != -1) {
                out.write(buffer, 0, n)
            }
            return out.toByteArray()
        } finally {
            input.close()
        }
    }

    static Document parseHtml(byte[] html) {
        HtmlCleaner cleaner = new HtmlCleaner()

        CleanerProperties props = cleaner.getProperties()

        props.setTranslateSpecialEntities(true)
        props.setTransResCharsToNCR(true)
        props.setOmitComments(true)
        props.setAllowHtmlInsideAttributes(true)
        props.setAllowMultiWordAttributes(true)
        props.setRecognizeUnicodeChars(true)

        // Same charset as cleaner.clean(URL).
        TagNode node = cleaner.clean(new ByteArrayInputStream(html), props.getCharset())
        //assert page.status_code == 200
        //assert "<title>LiveJournal Bot Policy</title>" not in page.text
        return new DomSerializer(props).createDOM(node)
    }

    /**
     * Raw HTML of the pages of one post, shared by all markups, so each page
     * is requested from LiveJournal once. get() parses a fresh DOM every time;
     * only the caller keeps it, so at most one DOM per caller is alive. A
     * failed URL is remembered so that the other markups do not request it
     * again.
     */
    static class PageCache {
        private static final Logger log = LoggerFactory.getLogger(PageCache.class)
        // insertion order: the oldest page is dropped first
        private final LinkedHashMap<String, byte[]> pages = new LinkedHashMap<String, byte[]>()
        private final Map<String, Exception> failed = new HashMap<String, Exception>()
        private long bytes = 0

        Document get(String url) {
            // Qualified call: an unqualified one from this inner class goes
            // through methodMissing, which turns the byte[] into a List.
            return CommentsClient.parseHtml(getHtml(url))
        }

        byte[] getHtml(String url) {
            Exception previous = failed[url]
            if (previous != null) {
                throw previous
            }
            byte[] html = pages[url]
            if (html == null) {
                try {
                    html = CommentsClient.downloadHtml(url)
                } catch (Exception e) {
                    log.warn("Failed to load comments page {}: {}", url, e.toString())
                    failed[url] = e
                    throw e
                }
                pages[url] = html
                bytes += html.length
                // keep at least the page just loaded
                while (bytes > CommentsClient.PAGE_CACHE_MAX_BYTES && pages.size() > 1) {
                    String oldest = pages.keySet().iterator().next()
                    bytes -= pages.remove(oldest).length
                }
            }
            return html
        }

        int size() { pages.size() }

        long sizeInBytes() { bytes }
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
