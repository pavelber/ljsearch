package org.ljsearch.entity

import org.ljsearch.IndexedType

import javax.persistence.Entity
import javax.persistence.EnumType
import javax.persistence.Enumerated
import javax.persistence.GeneratedValue
import javax.persistence.GenerationType
import javax.persistence.Id
import javax.persistence.Table

/**
 * Created by Pavel on 11/17/2015.
 */
@Entity
@Table(name="records")
class Record {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    protected Long id;
    String title, poster, text, url, journal

    Date date
    @Enumerated(EnumType.STRING)
    IndexedType type

    Record(){


    }

    Record(
            final String title,  final String text, final String journal,
            final String poster,  final String url, final Date date, final IndexedType type) {
        this.title = title
        this.poster = poster
        this.text = text
        this.url = url
        this.journal = journal
        this.date = date
        this.type = type
    }

    boolean equals(final o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        final Record record = (Record) o

        if (url != record.url) return false

        return true
    }

    int hashCode() {
        return (url != null ? url.hashCode() : 0)
    }
}
