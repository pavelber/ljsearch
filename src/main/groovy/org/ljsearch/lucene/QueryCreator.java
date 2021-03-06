package org.ljsearch.lucene;


import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.util.BytesRef;
import org.ljsearch.IndexedType;
import org.ljsearch.entity.IJournalRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class QueryCreator {

    @Autowired
    private IJournalRepository repo;

    public Query generate(String story) throws ParseException {
        QueryParser parser = new MultiFieldQueryParser(
                new String[]{
                        LuceneBinding.TITLE_FIELD,
                        LuceneBinding.CONTENT_FIELD,
                        /* Russian */
                        LuceneBinding.RUS_TITLE_FIELD,
                        LuceneBinding.RUS_CONTENT_FIELD,
                        /* English */
                        LuceneBinding.ENG_TITLE_FIELD,
                        LuceneBinding.ENG_CONTENT_FIELD,
                        /* Hebrew */
                        // LuceneBinding.HEB_TITLE_FIELD,
                        // LuceneBinding.HEB_CONTENT_FIELD
                },
                LuceneBinding.getAnalyzer());

        /* Operator OR is used by default */

        parser.setDefaultOperator(QueryParser.Operator.AND);

        return parser.parse(QueryParser.escape(story));
    }


    public Query generate(String words, String journal, String poster, Date dateFrom, Date dateTo, IndexedType type) throws ParseException {

        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        if (!StringUtils.isEmpty(words)) {
            Query qf = generate(words);
            builder = builder.add(qf, BooleanClause.Occur.MUST);
        }

        if (!StringUtils.isEmpty(journal)) {
            Query qf = new TermQuery(new Term(LuceneBinding.JOURNAL_FIELD, journal));
            builder = builder.add(qf, BooleanClause.Occur.MUST);
        } else {
            BooleanQuery.Builder journalBuilder = new BooleanQuery.Builder();
            getPublicJournalsNames().forEach(j -> {
                TermQuery catQuery1 = new TermQuery(new Term(LuceneBinding.JOURNAL_FIELD, j));
                journalBuilder.add(new BooleanClause(catQuery1, BooleanClause.Occur.SHOULD));
            });
            builder = builder.add(new BooleanClause(journalBuilder.build(), BooleanClause.Occur.MUST));
        }


        if (!StringUtils.isEmpty(poster)) {
            Query qp = new TermQuery(new Term(LuceneBinding.POSTER_FIELD, poster));
            builder = builder.add(qp, BooleanClause.Occur.MUST);
        }

        if (type != null) {
            Query qp = new TermQuery(new Term(LuceneBinding.TYPE_FIELD, type.toString().toLowerCase()));
            builder = builder.add(qp, BooleanClause.Occur.MUST);
        }

        if (dateFrom != null || dateTo != null) {
            TermRangeQuery dateQuery = new TermRangeQuery(LuceneBinding.DATE_FIELD,
                    dateFrom == null ? null : new BytesRef(DateHelper.toString(dateFrom)),
                    dateTo == null ? null : new BytesRef(DateHelper.toString(dateTo)), true, false);
            builder = builder.add(dateQuery, BooleanClause.Occur.MUST);
        }


        return builder.build();
    }

    private List<String> getPublicJournalsNames() {
        return repo.findByPrrivate(false).stream().map(j -> j.getJournal()).collect(Collectors.toList());
    }
}