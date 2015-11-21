package org.ljsearch.lucene;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.document.SortedDocValuesField;
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

        String content = Jsoup.parse(html).text();

        Document doc = new Document();

        String value = DateHelper.toString(date);
        doc.add(new Field(LuceneBinding.DATE_FIELD,
                value,
                Field.Store.YES, Field.Index.NOT_ANALYZED));
        doc.add(new SortedDocValuesField(LuceneBinding.DATE_FIELD, new BytesRef(value)));
        addField(url, doc, LuceneBinding.URL_FIELD);
        addField(journal, doc, LuceneBinding.JOURNAL_FIELD);
        addField(type.name(), doc, LuceneBinding.TYPE_FIELD);
        addField(poster, doc, LuceneBinding.POSTER_FIELD);
        addTextField(title, doc, LuceneBinding.TITLE_FIELD, LuceneBinding.RUS_TITLE_FIELD, LuceneBinding.ENG_TITLE_FIELD);
        addTextField(content, doc, LuceneBinding.CONTENT_FIELD, LuceneBinding.RUS_TITLE_FIELD, LuceneBinding.ENG_CONTENT_FIELD);


        try {
            synchronized (LuceneIndexer.class) {
                if (null != indexWriter) {
                    indexWriter.addDocument(doc);
                }
            }
        } catch (IOException ex) {
            logger.error(ex);
            throw new RuntimeException(ex);
        }


    }

    @Override
    public void commit() throws IOException {
        indexWriter.commit();
    }

    private void addField(final String text, final Document doc, final String titleField) {
        if (text != null) {
            doc.add(new Field(titleField,
                    text, Store.YES, Index.ANALYZED,
                    TermVector.WITH_POSITIONS_OFFSETS));
        }
    }

    private void addTextField(final String text, final Document doc, final String titleField,
                              final String rusTitleField, final String engContentField) {
        if (text != null) {
            doc.add(new Field(titleField,
                    text, Store.YES, Index.ANALYZED,
                    TermVector.WITH_POSITIONS_OFFSETS));
            doc.add(new Field(rusTitleField,
                    text, Store.NO, Index.ANALYZED,
                    TermVector.WITH_POSITIONS_OFFSETS));
            doc.add(new Field(engContentField,
                    text, Store.NO, Index.ANALYZED,
                    TermVector.WITH_POSITIONS_OFFSETS));
        }
    }

    public void setIndexDir(final String indexDir) {
        this.indexDir = indexDir;
    }
}