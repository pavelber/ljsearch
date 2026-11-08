package org.ljsearch.tools;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SimpleCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Bits;
import org.ljsearch.IndexedType;
import org.ljsearch.lucene.LuceneBinding;
import org.ljsearch.lucene.LuceneIndexer;
import org.ljsearch.lucene.LuceneSearcher;
import org.ljsearch.lucene.Post;
import org.ljsearch.lucene.QueryCreator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Offline maintenance of the ljsearch Lucene index. Never run it against the
 * live index directory: "rebuild" writes only to a new directory, the other
 * commands only read.
 *
 * <pre>
 *   dump    INDEX OUT          one line per kept document (type:journal:url): journal, type, date, hash of stored fields
 *   rebuild OLD NEW            copy every live document into NEW, one per type:journal:url, no term vectors
 *   queries INDEX OUT N SEED   sample N control queries (journal + word from a random document)
 *   search  INDEX QUERIES OUT  for each query: all matching type:journal:url ids, plus the top-400 result page
 *   segments INDEX OUT         segment names of the latest commit (the snapshot the rebuild starts from)
 *   delta   LIVE NEW SEGMENTS  copy into NEW the documents of LIVE segments not listed in SEGMENTS
 *   count   INDEX              maxDoc, numDocs, segments
 * </pre>
 * "delta" only reads LIVE, so it may run while the application writes to it.
 */
public class IndexTool {

    static final String[] STORED = {
            LuceneBinding.DATE_FIELD, LuceneBinding.URL_FIELD, LuceneBinding.JOURNAL_FIELD,
            LuceneBinding.TYPE_FIELD, LuceneBinding.POSTER_FIELD, LuceneBinding.TITLE_FIELD,
            LuceneBinding.CONTENT_FIELD};

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "dump":
                dump(Paths.get(args[1]), Paths.get(args[2]), canonical(args, 3));
                break;
            case "rebuild":
                rebuild(Paths.get(args[1]), Paths.get(args[2]), canonical(args, 3));
                break;
            case "queries":
                queries(Paths.get(args[1]), Paths.get(args[2]), Integer.parseInt(args[3]), Long.parseLong(args[4]));
                break;
            case "search":
                search(Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]), args.length > 4 ? Paths.get(args[4]) : null);
                break;
            case "segments":
                segments(Paths.get(args[1]), Paths.get(args[2]));
                break;
            case "delta":
                delta(Paths.get(args[1]), Paths.get(args[2]), Paths.get(args[3]), canonical(args, 4));
                break;
            case "show": {
                // show INDEX ID...   every live copy of the given items, in index order
                java.util.Set<String> wanted = new java.util.HashSet<>(java.util.Arrays.asList(args).subList(2, args.length));
                try (Directory dir = FSDirectory.open(Paths.get(args[1])); DirectoryReader r = DirectoryReader.open(dir)) {
                    forEachLiveDoc(r, d -> {
                        String id = id(d);
                        if (wanted.contains(id)) {
                            System.out.println("== " + id + " hash=" + hash(d));
                            for (String f : STORED) {
                                String v = d.get(f);
                                if (v != null && v.length() > 400) {
                                    v = v.substring(0, 400) + "...";
                                }
                                System.out.println("   " + f + "=" + (v == null ? null : v.replace('\n', ' ').replace('\r', ' ')));
                            }
                        }
                    });
                }
                break;
            }
            case "kinds": {
                // kinds INDEX CANONICAL   classify duplicates among copies with canonical journals
                java.util.Set<String> canon = canonical(args, 2);
                Map<String, String> first = new HashMap<>();          // id -> journal|hash of first canonical copy
                Map<String, java.util.Set<String>> journals = new HashMap<>();
                Map<String, java.util.Set<String>> hashes = new HashMap<>();
                try (Directory dir = FSDirectory.open(Paths.get(args[1])); DirectoryReader r = DirectoryReader.open(dir)) {
                    forEachLiveDoc(r, d -> {
                        String j = d.get(LuceneBinding.JOURNAL_FIELD);
                        if (!canon.contains(j)) {
                            return;
                        }
                        String id = key(d);
                        String h = hash(d);
                        String prev = first.putIfAbsent(id, j + "|" + h);
                        if (prev != null) {
                            journals.computeIfAbsent(id, k -> new java.util.HashSet<>(java.util.Collections.singleton(prev.split("\\|")[0]))).add(j);
                            hashes.computeIfAbsent(id, k -> new java.util.HashSet<>(java.util.Collections.singleton(prev.split("\\|")[1]))).add(h);
                        }
                    });
                }
                long multiJournal = 0, versions = 0;
                TreeMap<String, Long> pairs = new TreeMap<>();
                for (Map.Entry<String, java.util.Set<String>> e : journals.entrySet()) {
                    if (e.getValue().size() > 1) {
                        multiJournal++;
                        pairs.merge(new TreeSet<>(e.getValue()).toString(), 1L, Long::sum);
                    }
                }
                for (Map.Entry<String, java.util.Set<String>> e : hashes.entrySet()) {
                    // different content in the same journal: an edited version
                    if (journals.get(e.getKey()).size() == 1 && e.getValue().size() > 1) {
                        versions++;
                    }
                }
                System.out.println("idsWithCanonicalDuplicates=" + journals.size() + " inSeveralJournals=" + multiJournal
                        + " sameJournalDifferentContent=" + versions);
                for (Map.Entry<String, Long> e : pairs.entrySet()) {
                    System.out.println("  " + e.getKey() + " " + e.getValue());
                }
                break;
            }
            case "count":
                try (Directory dir = FSDirectory.open(Paths.get(args[1])); DirectoryReader r = DirectoryReader.open(dir)) {
                    System.out.println("maxDoc=" + r.maxDoc() + " numDocs=" + r.numDocs() + " segments=" + r.leaves().size());
                }
                break;
            default:
                throw new IllegalArgumentException(args[0]);
        }
    }

    /**
     * Journal names as the database has them (first column of the file).
     * Search filters by these exact names, so of several copies of an item
     * the one with such a name is the findable one and must be kept. Older
     * copies carry other spellings ("inetshop-il" for "inetshop_il") or
     * journals no longer in the database.
     */
    static java.util.Set<String> canonical(String[] args, int i) throws IOException {
        if (args.length <= i) {
            return null;
        }
        java.util.Set<String> names = new java.util.HashSet<>();
        for (String line : Files.readAllLines(Paths.get(args[i]), StandardCharsets.UTF_8)) {
            if (!line.trim().isEmpty()) {
                names.add(line.split("	")[0].trim());
            }
        }
        System.out.println("canonical journals: " + names.size());
        return names;
    }

    /**
     * Which copies to keep. Copies under a canonical journal are always kept
     * (one per journal: the id contains the journal and the later copy replaces
     * the earlier one). A copy under another spelling is kept only when the
     * item has no canonical copy at all. withCanonical: keys (type:url) that
     * have a canonical copy, from a first pass; null without canonical list.
     */
    static boolean keep(java.util.Set<String> canonical, java.util.Set<String> withCanonical, Document d) throws Exception {
        if (canonical == null || canonical.contains(d.get(LuceneBinding.JOURNAL_FIELD))) {
            return true;
        }
        return !withCanonical.contains(key(d));
    }

    /** First pass: keys (type:url) of items that have a copy under a canonical journal. */
    static java.util.Set<String> withCanonical(IndexReader reader, java.util.Set<String> canonical) throws Exception {
        java.util.Set<String> keys = new java.util.HashSet<>();
        if (canonical != null) {
            forEachLiveDoc(reader, d -> {
                if (canonical.contains(d.get(LuceneBinding.JOURNAL_FIELD))) {
                    keys.add(key(d));
                }
            });
            System.out.println("items with a canonical copy: " + keys.size());
        }
        return keys;
    }

    /** Document id as the application writes it: type:journal:url (type:journal:nourl:HASH without URL). */
    static String id(Document d) throws Exception {
        String url = d.get(LuceneBinding.URL_FIELD);
        return LuceneIndexer.idValue(d.get(LuceneBinding.TYPE_FIELD), d.get(LuceneBinding.JOURNAL_FIELD),
                url == null ? "nourl:" + hash(d) : url);
    }

    /** The item regardless of journal: type:url. */
    static String key(Document d) throws Exception {
        String url = d.get(LuceneBinding.URL_FIELD);
        return d.get(LuceneBinding.TYPE_FIELD) + ":" + (url == null ? "nourl:" + hash(d) : url);
    }

    /** Same document in the new format, always with an id field, so writing it again replaces it. */
    static void write(IndexWriter writer, Document d) throws Exception {
        String url = d.get(LuceneBinding.URL_FIELD);
        Document nd = LuceneIndexer.buildDocument(d.get(LuceneBinding.DATE_FIELD), url,
                d.get(LuceneBinding.JOURNAL_FIELD), d.get(LuceneBinding.TYPE_FIELD), d.get(LuceneBinding.POSTER_FIELD),
                d.get(LuceneBinding.TITLE_FIELD), d.get(LuceneBinding.CONTENT_FIELD));
        String id = id(d);
        if (url == null) {
            nd.add(new org.apache.lucene.document.StringField(LuceneBinding.ID_FIELD, id,
                    org.apache.lucene.document.Field.Store.NO));
        }
        writer.updateDocument(new Term(LuceneBinding.ID_FIELD, id), nd);
    }

    static String hash(Document d) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        for (String f : STORED) {
            String v = d.get(f);
            md.update((f + "=" + (v == null ? "\u0000null" : v) + "\u0001").getBytes(StandardCharsets.UTF_8));
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    interface DocVisitor {
        void visit(Document d) throws Exception;
    }

    /** Live documents in index order (segment by segment, doc id order). */
    static void forEachLiveDoc(IndexReader reader, DocVisitor visitor) throws Exception {
        for (LeafReaderContext ctx : reader.leaves()) {
            LeafReader leaf = ctx.reader();
            Bits live = leaf.getLiveDocs();
            for (int i = 0; i < leaf.maxDoc(); i++) {
                if (live == null || live.get(i)) {
                    visitor.visit(leaf.document(i));
                }
            }
        }
    }

    static void dump(Path index, Path out, java.util.Set<String> canonical) throws Exception {
        try (Directory dir = FSDirectory.open(index); DirectoryReader reader = DirectoryReader.open(dir)) {
            // last copy of each id wins, the same rule as rebuild
            Map<String, String> last = new HashMap<>();
            Map<String, Integer> copies = new HashMap<>();
            Map<String, TreeSet<String>> hashesOfDuplicates = new HashMap<>();
            long[] noUrl = {0};
            java.util.Set<String> withCanonical = withCanonical(reader, canonical);
            forEachLiveDoc(reader, d -> {
                if (!keep(canonical, withCanonical, d)) {
                    return;
                }
                String id = id(d);
                String line = d.get(LuceneBinding.JOURNAL_FIELD) + "\t" + d.get(LuceneBinding.TYPE_FIELD) + "\t"
                        + d.get(LuceneBinding.DATE_FIELD) + "\t" + hash(d);
                if (d.get(LuceneBinding.URL_FIELD) == null) {
                    noUrl[0]++;
                }
                String previous = last.put(id, line);
                int n = copies.merge(id, 1, Integer::sum);
                if (previous != null) {
                    TreeSet<String> h = hashesOfDuplicates.computeIfAbsent(id, k -> new TreeSet<>());
                    h.add(previous.substring(previous.lastIndexOf('\t') + 1));
                    h.add(line.substring(line.lastIndexOf('\t') + 1));
                }
            });
            TreeMap<String, String> sorted = new TreeMap<>(last);
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
                for (Map.Entry<String, String> e : sorted.entrySet()) {
                    w.println(e.getKey() + "\t" + e.getValue());
                }
            }
            long duplicateDocs = 0, differing = 0;
            for (Map.Entry<String, Integer> e : copies.entrySet()) {
                duplicateDocs += e.getValue() - 1;
            }
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Paths.get(out + ".duplicates"), StandardCharsets.UTF_8))) {
                for (Map.Entry<String, TreeSet<String>> e : new TreeMap<>(hashesOfDuplicates).entrySet()) {
                    boolean differ = e.getValue().size() > 1;
                    if (differ) {
                        differing++;
                    }
                    w.println(e.getKey() + "\t" + copies.get(e.getKey()) + "\t" + (differ ? "DIFFERENT" : "same"));
                }
            }
            TreeMap<String, long[]> perJournal = new TreeMap<>();
            for (String line : sorted.values()) {
                String[] p = line.split("\t");
                long[] c = perJournal.computeIfAbsent(p[0], k -> new long[2]);
                c["Post".equals(p[1]) ? 0 : 1]++;
            }
            try (PrintWriter w = new PrintWriter(Files.newBufferedWriter(Paths.get(out + ".journals"), StandardCharsets.UTF_8))) {
                for (Map.Entry<String, long[]> e : perJournal.entrySet()) {
                    w.println(e.getKey() + "\tposts=" + e.getValue()[0] + "\tcomments=" + e.getValue()[1]);
                }
            }
            System.out.println("index=" + index);
            System.out.println("maxDoc=" + reader.maxDoc() + " numDocs=" + reader.numDocs() + " segments=" + reader.leaves().size());
            System.out.println("uniqueIds=" + sorted.size() + " docsWithoutUrl=" + noUrl[0]);
            System.out.println("duplicateDocs=" + duplicateDocs + " idsWithDuplicates=" + hashesOfDuplicates.size()
                    + " ofThemWithDifferentContent=" + differing);
            System.out.println("dateMin=" + (sorted.isEmpty() ? "" : minMax(sorted.values(), true))
                    + " dateMax=" + (sorted.isEmpty() ? "" : minMax(sorted.values(), false)));
        }
    }

    static String minMax(Iterable<String> lines, boolean min) {
        String best = null;
        for (String line : lines) {
            String d = line.split("\t")[2];
            if (best == null || (min ? d.compareTo(best) < 0 : d.compareTo(best) > 0)) {
                best = d;
            }
        }
        return best;
    }

    static void rebuild(Path oldIndex, Path newIndex, java.util.Set<String> canonical) throws Exception {
        if (Files.exists(newIndex) && Files.list(newIndex).findAny().isPresent()) {
            throw new IllegalStateException("target is not empty: " + newIndex);
        }
        long start = System.currentTimeMillis();
        long[] n = {0, 0};
        try (Directory oldDir = FSDirectory.open(oldIndex); DirectoryReader reader = DirectoryReader.open(oldDir);
             Directory newDir = FSDirectory.open(newIndex)) {
            IndexWriterConfig config = new IndexWriterConfig(LuceneBinding.getAnalyzer());
            config.setOpenMode(IndexWriterConfig.OpenMode.CREATE);
            config.setRAMBufferSizeMB(256);
            TieredMergePolicy mergePolicy = new TieredMergePolicy();
            // reclaim every deleted (replaced) copy, not only above the 10% default
            mergePolicy.setForceMergeDeletesPctAllowed(0.0);
            config.setMergePolicy(mergePolicy);
            try (IndexWriter writer = new IndexWriter(newDir, config)) {
                java.util.Set<String> withCanonical = withCanonical(reader, canonical);
                forEachLiveDoc(reader, d -> {
                    if (keep(canonical, withCanonical, d)) {
                        write(writer, d);
                    } else {
                        n[1]++;
                    }
                    if (++n[0] % 200000 == 0) {
                        System.out.println("  " + n[0] + " docs, " + (System.currentTimeMillis() - start) / 1000 + " s");
                    }
                });
                // updateDocument leaves the replaced copies as deleted docs;
                // reclaim their space now
                writer.forceMergeDeletes();
                writer.commit();
            }
        }
        System.out.println("rebuilt " + n[0] + " source docs (" + n[1] + " non-canonical copies skipped) in " + (System.currentTimeMillis() - start) / 1000 + " s");
    }

    /** Names of the segments of the latest commit, one per line. */
    static void segments(Path index, Path out) throws Exception {
        try (Directory dir = FSDirectory.open(index); DirectoryReader reader = DirectoryReader.open(dir);
             PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            for (LeafReaderContext ctx : reader.leaves()) {
                SegmentReader s = (SegmentReader) ctx.reader();
                w.println(s.getSegmentName() + "\t" + s.maxDoc() + "\t" + s.numDocs());
            }
            System.out.println("commit=" + reader.getIndexCommit().getSegmentsFileName() + " segments=" + reader.leaves().size()
                    + " numDocs=" + reader.numDocs());
        }
    }

    /**
     * Copies into NEW every document of LIVE that lives in a segment which was
     * not in the snapshot the new index was built from. Write-once segments
     * with the same name hold the same documents, and the indexer never
     * deletes, so this brings NEW up to date with LIVE. Replacing by type:journal:url
     * makes it safe to run again, and a merged segment that also holds old
     * documents only re-writes them unchanged. LIVE is only read.
     */
    static void delta(Path live, Path newIndex, Path snapshotSegments, java.util.Set<String> canonical) throws Exception {
        java.util.Set<String> known = new java.util.HashSet<>();
        for (String line : Files.readAllLines(snapshotSegments, StandardCharsets.UTF_8)) {
            if (!line.isEmpty()) {
                known.add(line.split("\t")[0]);
            }
        }
        long start = System.currentTimeMillis();
        long copied = 0, skipped = 0;
        try (Directory liveDir = FSDirectory.open(live); DirectoryReader reader = DirectoryReader.open(liveDir);
             Directory newDir = FSDirectory.open(newIndex)) {
            IndexWriterConfig config = new IndexWriterConfig(LuceneBinding.getAnalyzer());
            config.setOpenMode(IndexWriterConfig.OpenMode.APPEND);
            try (IndexWriter writer = new IndexWriter(newDir, config)) {
                System.out.println("live commit=" + reader.getIndexCommit().getSegmentsFileName() + " numDocs=" + reader.numDocs()
                        + "; new index numDocs before=" + writer.numDocs());
                for (LeafReaderContext ctx : reader.leaves()) {
                    SegmentReader s = (SegmentReader) ctx.reader();
                    if (known.contains(s.getSegmentName())) {
                        continue;
                    }
                    Bits liveDocs = s.getLiveDocs();
                    long n = 0;
                    for (int i = 0; i < s.maxDoc(); i++) {
                        if (liveDocs != null && !liveDocs.get(i)) {
                            continue;
                        }
                        Document d = s.document(i);
                        // The application writes journal names from the database, so a
                        // non-canonical copy is an old one that a merge moved here; the
                        // rebuild already chose between it and its canonical copy.
                        if (canonical != null && !canonical.contains(d.get(LuceneBinding.JOURNAL_FIELD))) {
                            skipped++;
                            continue;
                        }
                        write(writer, d);
                        n++;
                    }
                    copied += n;
                    System.out.println("  segment " + s.getSegmentName() + ": " + n + " docs");
                }
                writer.commit();
                System.out.println("copied " + copied + " docs, skipped " + skipped + " non-canonical, in " + (System.currentTimeMillis() - start) / 1000
                        + " s; new index numDocs after=" + writer.numDocs());
            }
        }
    }

    static final Pattern WORD =Pattern.compile("[\\p{IsAlphabetic}]{5,15}");

    static void queries(Path index, Path out, int count, long seed) throws Exception {
        Random random = new Random(seed);
        try (Directory dir = FSDirectory.open(index); DirectoryReader reader = DirectoryReader.open(dir);
             PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8))) {
            int written = 0, tries = 0;
            Bits live = org.apache.lucene.index.MultiFields.getLiveDocs(reader);
            while (written < count && tries++ < count * 50) {
                int docId = random.nextInt(reader.maxDoc());
                if (live != null && !live.get(docId)) {
                    continue;
                }
                Document d = reader.document(docId);
                String content = d.get(LuceneBinding.CONTENT_FIELD);
                if (content == null) {
                    continue;
                }
                List<String> words = new ArrayList<>();
                Matcher m = WORD.matcher(content);
                while (m.find()) {
                    words.add(m.group().toLowerCase());
                }
                if (words.isEmpty()) {
                    continue;
                }
                String word = words.get(random.nextInt(words.size()));
                String journal = d.get(LuceneBinding.JOURNAL_FIELD);
                // every 4th query also filters by type, so both code paths are covered
                String type = written % 4 == 3 ? d.get(LuceneBinding.TYPE_FIELD) : "";
                w.println(journal + "\t" + word + "\t" + type);
                written++;
            }
            System.out.println("queries=" + written);
        }
    }

    /**
     * Queries without a journal search all public journals, which the
     * application reads from the database. Offline they come from a
     * "journal TAB private" export (mysql -B; private is \0 for false).
     */
    static void setPublicJournals(QueryCreator creator, Path journalsFile) throws Exception {
        List<org.ljsearch.entity.Journal> pub = new ArrayList<>();
        for (String line : Files.readAllLines(journalsFile, StandardCharsets.UTF_8)) {
            String[] p = line.split("\t", -1);
            if (p.length > 1 && (p[1].equals("\\0") || p[1].equals("0") || p[1].isEmpty())) {
                org.ljsearch.entity.Journal j = new org.ljsearch.entity.Journal();
                j.setJournal(p[0].trim());
                pub.add(j);
            }
        }
        System.out.println("public journals: " + pub.size());
        Object repo = java.lang.reflect.Proxy.newProxyInstance(IndexTool.class.getClassLoader(),
                new Class<?>[]{org.ljsearch.entity.IJournalRepository.class}, (proxy, m, a) -> {
                    if (m.getName().equals("findByPrrivate") && Boolean.FALSE.equals(a[0])) {
                        return pub;
                    }
                    throw new UnsupportedOperationException(m.getName());
                });
        java.lang.reflect.Field f = QueryCreator.class.getDeclaredField("repo");
        f.setAccessible(true);
        f.set(creator, repo);
    }

    static void search(Path index, Path queries, Path out, Path journalsFile) throws Exception {
        QueryCreator creator = new QueryCreator();
        if (journalsFile != null) {
            setPublicJournals(creator, journalsFile);
        }
        try (Directory dir = FSDirectory.open(index); DirectoryReader reader = DirectoryReader.open(dir);
             PrintWriter w = new PrintWriter(Files.newBufferedWriter(out, StandardCharsets.UTF_8));
             BufferedReader in = Files.newBufferedReader(queries, StandardCharsets.UTF_8)) {
            IndexSearcher searcher = new IndexSearcher(reader);
            LuceneSearcher page = new LuceneSearcher();
            page.setQueryCreator(creator);
            page.init(dir);
            String line;
            while ((line = in.readLine()) != null) {
                String[] p = line.split("\t", -1);
                IndexedType type = p[2].isEmpty() ? null : IndexedType.valueOf(p[2]);
                Query q = creator.generate(p[1], p[0], "", null, null, type);
                TreeSet<String> ids = new TreeSet<>();
                List<Integer> hits = new ArrayList<>();
                searcher.search(q, new SimpleCollector() {
                    int base;

                    @Override
                    protected void doSetNextReader(LeafReaderContext context) {
                        base = context.docBase;
                    }

                    @Override
                    public void collect(int doc) {
                        hits.add(base + doc);
                    }

                    @Override
                    public boolean needsScores() {
                        return false;
                    }
                });
                for (int doc : hits) {
                    Document d = searcher.doc(doc);
                    String id = id(d);
                    ids.add(id);
                }
                w.println("Q\t" + line);
                w.println("ALL\t" + ids.size() + "\t" + String.join(" ", ids));
                List<Post> posts = page.search(p[0], "", p[1], null, null, type);
                StringBuilder sb = new StringBuilder();
                for (Post post : posts) {
                    sb.append(post.getType()).append(':').append(post.getUrl()).append('|')
                            .append(post.getDate()).append('|').append(post.getTitle()).append('|')
                            .append(post.getText().replace('\n', ' ')).append('\u0002');
                }
                w.println("PAGE\t" + posts.size() + "\t" + sb);
            }
        }
    }
}
