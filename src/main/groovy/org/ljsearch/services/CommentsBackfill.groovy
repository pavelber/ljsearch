package org.ljsearch.services

import groovy.transform.CompileStatic
import org.apache.lucene.document.DateTools
import org.apache.lucene.document.Document
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.Term
import org.apache.lucene.search.BooleanClause
import org.apache.lucene.search.BooleanQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.Query
import org.apache.lucene.search.ScoreDoc
import org.apache.lucene.search.TermQuery
import org.apache.lucene.search.TermRangeQuery
import org.apache.lucene.store.Directory
import org.apache.lucene.store.FSDirectory
import org.ljsearch.IndexedType
import org.ljsearch.comments.Comment
import org.ljsearch.comments.CommentsClient
import org.ljsearch.lucene.IIndexer
import org.ljsearch.lucene.LuceneBinding
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.PropertySource
import org.springframework.stereotype.Service

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * One-off: loads the comments of posts indexed without them.
 *
 * From about 2025-07-28 LJ pages carried the comments only as Site.page JSON,
 * which the old markups did not read, so every post indexed from then until
 * the fix of 2026-11-16 has no comments. The posts themselves are in the index
 * (url, journal, date), so only their public pages are loaded, no XML-RPC.
 *
 * Runs in the downloader thread after the journals, so it never runs in
 * parallel with them. Progress is kept in a file, one "journal TAB url" per
 * finished post, so a redeploy or restart continues where it stopped. Any
 * failure that may mean trouble with LJ stops it until the next daily run.
 */
@Service
@CompileStatic
@PropertySource("classpath:ljsearch.properties")
class CommentsBackfill {

    private static Logger logger = LoggerFactory.getLogger(CommentsBackfill.class)

    // Posts of these dates (DateHelper format, minute resolution) are back-filled.
    static final String FROM = "202507010000"
    static final String TO = "202611170000"

    static final String DONE_FILE = "comments-backfill.done.txt"

    // Pause after every post; the regular downloader uses 10 s.
    static final long PAUSE_MS = 15 * 1000L

    // This many posts in a row without the comments JSON look like a block page.
    static final int MAX_NO_JSON_IN_ROW = 3

    @Value('${index.dir}')
    String indexDir

    @Autowired
    IIndexer indexer

    @Autowired
    CommentsClient commentsClient

    static class Target {
        String journal, url
        Date date

        String key() { journal + "\t" + url }
    }

    void run() {
        Path dir = Paths.get(indexDir)
        Path doneFile = dir.resolve(DONE_FILE)
        Set<String> done = loadDone(doneFile)
        List<Target> targets = findPostsWithoutComments(dir).findAll { Target t -> !done.contains(t.key()) }
        if (targets.isEmpty()) {
            logger.info("Comments back-fill: nothing left")
            return
        }
        logger.info("Comments back-fill: {} posts left ({} done before)", targets.size(), done.size())

        int noJsonInRow = 0
        int posts = 0
        int comments = 0
        for (Target t : targets) {
            Collection<Comment> found
            try {
                found = fetchComments(t.url)
            } catch (InterruptedException e) {
                throw e
            } catch (FileNotFoundException e) {
                // deleted post (404/410): nothing to load, do not ask again
                logger.info("Comments back-fill: {} is gone ({})", t.url, e.toString())
                markDone(doneFile, t)
                pause()
                continue
            } catch (CommentsClient.NoCommentsJsonException e) {
                noJsonInRow++
                logger.warn("Comments back-fill: {} ({} in a row)", e.getMessage(), noJsonInRow)
                if (noJsonInRow >= MAX_NO_JSON_IN_ROW) {
                    logger.warn("Comments back-fill: stopped until the next run, pages without comments JSON")
                    return
                }
                pause()
                continue
            } catch (Exception e) {
                logger.warn("Comments back-fill: stopped until the next run after " + t.url, e)
                return
            }
            noJsonInRow = 0
            int added = 0
            for (Comment c : found) {
                if (c.text == null) {
                    continue
                }
                try {
                    indexer.add("", c.text, t.journal, c.user, c.link, c.date ?: t.date, IndexedType.Comment)
                    added++
                } catch (Exception e) {
                    logger.error("Comments back-fill: failed to index comment " + c.link, e)
                }
            }
            indexer.commit()
            markDone(doneFile, t)
            posts++
            comments += added
            if (posts % 50 == 0) {
                logger.info("Comments back-fill: {} of {} posts, {} comments", posts, targets.size(), comments)
            }
            pause()
        }
        logger.info("Comments back-fill: finished, {} posts, {} comments", posts, comments)
    }

    protected Collection<Comment> fetchComments(String url) {
        return commentsClient.getCommentsOrFail(url)
    }

    protected void pause() {
        Thread.sleep(PAUSE_MS)
    }

    /**
     * Posts dated FROM..TO that have no comment in the index, oldest first.
     * Reads the last commit with its own reader; the writer stays the app's.
     */
    static List<Target> findPostsWithoutComments(Path indexPath) {
        Directory dir = FSDirectory.open(indexPath)
        DirectoryReader reader = DirectoryReader.open(dir)
        try {
            IndexSearcher searcher = new IndexSearcher(reader)
            // a comment is never older than its post, so comments from FROM on cover them all
            Set<String> withComments = new HashSet<String>()
            for (Document d : find(searcher, reader, IndexedType.Comment, FROM, null)) {
                String url = d.get(LuceneBinding.URL_FIELD)
                if (url != null) {
                    withComments.add(d.get(LuceneBinding.JOURNAL_FIELD) + "\t" + postKey(url))
                }
            }
            List<Target> targets = []
            Set<String> seen = new HashSet<String>()
            for (Document d : find(searcher, reader, IndexedType.Post, FROM, TO)) {
                String url = d.get(LuceneBinding.URL_FIELD)
                String journal = d.get(LuceneBinding.JOURNAL_FIELD)
                if (url == null || journal == null) {
                    continue
                }
                String key = journal + "\t" + postKey(url)
                if (withComments.contains(key) || !seen.add(key)) {
                    continue
                }
                Date date = null
                try {
                    date = DateTools.stringToDate(d.get(LuceneBinding.DATE_FIELD))
                } catch (Exception ignored) {
                }
                targets.add(new Target(journal: journal, url: url, date: date))
            }
            targets.sort { Target a, Target b -> (a.date ?: new Date(0)) <=> (b.date ?: new Date(0)) }
            logger.info("Comments back-fill: {} posts from {} to {} without comments ({} posts have some)",
                    targets.size(), FROM, TO, withComments.size())
            return targets
        } finally {
            reader.close()
            dir.close()
        }
    }

    private static List<Document> find(IndexSearcher searcher, DirectoryReader reader, IndexedType type,
                                       String from, String to) {
        BooleanQuery query = new BooleanQuery()
        // contenttype and date are analysed by StandardAnalyzer: "post"/"comment", "yyyyMMddHHmm"
        query.add(new TermQuery(new Term(LuceneBinding.TYPE_FIELD, type.name().toLowerCase())), BooleanClause.Occur.MUST)
        query.add(TermRangeQuery.newStringRange(LuceneBinding.DATE_FIELD, from, to, true, false), BooleanClause.Occur.MUST)
        // few hits (hundreds), so a top-N of exactly that size is cheap
        int count = searcher.count(query)
        List<Document> docs = []
        if (count == 0) {
            return docs
        }
        for (ScoreDoc hit : searcher.search((Query) query, count).scoreDocs) {
            docs.add(reader.document(hit.doc))
        }
        return docs
    }

    /** Post URL without scheme, query and anchor, lower case: the same for the post and its comments. */
    static String postKey(String url) {
        String u = url.split("#")[0].split("\\?")[0].toLowerCase()
        return u.replaceFirst("^https?://", "")
    }

    private static Set<String> loadDone(Path file) {
        Set<String> done = new HashSet<String>()
        if (Files.exists(file)) {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (!line.isEmpty()) {
                    done.add(line)
                }
            }
        }
        return done
    }

    private static void markDone(Path file, Target t) {
        Files.write(file, [t.key()], StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }
}
