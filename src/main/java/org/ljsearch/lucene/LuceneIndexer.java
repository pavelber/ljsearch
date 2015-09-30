package org.ljsearch.lucene;

import org.apache.log4j.Logger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Index;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.Field.TermVector;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Paths;

public class LuceneIndexer {
    private static final Logger logger = Logger.getLogger(LuceneIndexer.class.getName());

    /* IndexWriter is completely thread safe */

    private static IndexWriter indexWriter;

    public static void optimizeAndClose() {
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

    public LuceneIndexer(String indexDir, final OpenMode create) throws IOException {
        Directory dir = FSDirectory.open(Paths.get(indexDir));
        IndexWriterConfig config = new IndexWriterConfig(
                LuceneBinding.getAnalyzer());
        config.setOpenMode(create); // Rewrite old index
        indexWriter = new IndexWriter(dir, config);
    }

    public void add(String title, String html, String journal, String poster) {

        //String content = HtmlHelper.extractContent(html);
        String content = html;
        logger.info("***** " + title + " *****");
        if (null != title)
            logger.info(title);
        logger.info(content);

        Document doc = new Document();

        addField(journal, doc, LuceneBinding.JOURNAL_FIELD);
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
        }
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
}