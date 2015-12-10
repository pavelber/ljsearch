package org.ljsearch.lucene

import org.ljsearch.IndexedType;

class Post {
    String title
    String journal
    String poster
    String url
    String text
    IndexedType type
    long date

    boolean equals(final o) {
        if (this.is(o)) return true
        if (getClass() != o.class) return false

        final Post post = (Post) o

        if (url != post.url) return false

        return true
    }

    int hashCode() {
        return url.hashCode()
    }
}
