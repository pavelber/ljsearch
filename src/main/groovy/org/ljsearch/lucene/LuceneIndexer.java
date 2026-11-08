package org.ljsearch.lucene;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.BytesRef;
import org.jsoup.Jsoup;
import org.ljsearch.IndexedType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Date;

@Service
@PropertySource("classpath:ljsearch.properties")
public class LuceneIndexer implements IIndexer {
    private static final Logger logger = Logger.getLogger(LuceneIndexer.class.getName());

    /* IndexWriter is completely thread safe */
    protected IndexWriter indexWriter;

    @Value("${index.dir}")
    protected String indexDir;

    @Override
    @PreDestroy
    public void optimizeAndClose() {
        try {
            synchronized (LuceneIndexer.class) {
                if (null != indexWriter) {
                    indexWriter.close();
                    indexWriter = null;
                } else {
                    throw new IOException("Index already closed");
                }
            }
        } catch (IOException ex) {
            logger.error(ex);
        }
    }

    @PostConstruct
    public void init() throws Exception {
        Directory dir = FSDirectory.open(Paths.get(indexDir));
        init(dir);
        commit();
    }

    public void init(Directory dir) throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(
                LuceneBinding.getAnalyzer());
        config.setOpenMode(OpenMode.CREATE_OR_APPEND); // Rewrite old index
        indexWriter = new IndexWriter(dir, config);

    }

    @Override
    public void add(String title, String html, String journal, String poster, String url, final Date date, IndexedType type) {

        // LJ returns posts without a body (e.g. image-only or deleted text)
        String content = html == null ? "" : Jsoup.parse(html).text();
        String value = DateHelper.toString(date == null ? new Date() : date);

        Document doc = buildDocument(value, url, journal, type.name(), poster, title, content);

        try {
            synchronized (LuceneIndexer.class) {
                if (null != indexWriter) {
                    Term id = idTerm(type.name(), journal, url);
                    if (id != null) {
                        // Replaces an earlier copy of the same post/comment, so
                        // loading a day again does not create duplicates.
                        indexWriter.updateDocument(id, doc);
                    } else {
                        indexWriter.addDocument(doc);
                    }
                }
            }
        } catch (IOException ex) {
            logger.error(ex);
            throw new RuntimeException(ex);
        }


    }

    /**
     * Unique key of an indexed item: type, journal and URL. null when there is
     * no URL. The journal is part of the key because the same comment can be
     * loaded under two journals (a community and a member's journal), and
     * each copy must stay findable under its own journal.
     */
    public static Term idTerm(String type, String journal, String url) {
        return url == null ? null : new Term(LuceneBinding.ID_FIELD, idValue(type, journal, url));
    }

    public static String idValue(String type, String journal, String url) {
        return type + ":" + journal + ":" + url;
    }

    /**
     * Builds the Lucene document from the values that are stored in the index,
     * so the same code serves new items and rebuilding the index from its own
     * stored fields. date is already in DateHelper format; type is the
     * IndexedType name.
     *
     * No term vectors: nothing reads them (the highlighter re-analyses the
     * stored text), and they made up most of the index size.
     */
    public static Document buildDocument(String date, String url, String journal, String type,
                                         String poster, String title, String content) {
        Document doc = new Document();

        doc.add(new TextField(LuceneBinding.DATE_FIELD,
                date,
                Field.Store.YES));
        doc.add(new SortedDocValuesField(LuceneBinding.DATE_FIELD, new BytesRef(date)));

        if (url != null) {
            doc.add(new StringField(LuceneBinding.ID_FIELD, idValue(type, journal, url), Field.Store.NO));
        }
        addField(url, doc, LuceneBinding.URL_FIELD);
        addField(journal, doc, LuceneBinding.JOURNAL_FIELD);
        addField(type, doc, LuceneBinding.TYPE_FIELD);
        addField(poster, doc, LuceneBinding.POSTER_FIELD);
        addTextField(title, doc, LuceneBinding.TITLE_FIELD, LuceneBinding.RUS_TITLE_FIELD, LuceneBinding.ENG_TITLE_FIELD);
        addTextField(content, doc, LuceneBinding.CONTENT_FIELD, LuceneBinding.RUS_CONTENT_FIELD, LuceneBinding.ENG_CONTENT_FIELD);
        return doc;
    }

    @Override
    public void commit() throws IOException {
        indexWriter.commit();
    }

    private static void addField(final String text, final Document doc, final String titleField) {
        if (text != null) {
            doc.add(new Field(titleField,
                    text, Store.YES, Index.ANALYZED,
                    TermVector.NO));
        }
    }

    private static void addTextField(final String text, final Document doc, final String titleField,
                                     final String rusTitleField, final String engContentField) {
        if (text != null) {
            doc.add(new Field(titleField,
                    text, Store.YES, Index.ANALYZED,
                    TermVector.NO));
            doc.add(new Field(rusTitleField,
                    text, Store.NO, Index.ANALYZED,
                    TermVector.NO));
            doc.add(new Field(engContentField,
                    text, Store.NO, Index.ANALYZED,
                    TermVector.NO));
        }
    }

    public void setIndexDir(final String indexDir) {
        this.indexDir = indexDir;
    }
}