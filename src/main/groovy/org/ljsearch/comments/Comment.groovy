package org.ljsearch.comments

import groovy.transform.Canonical

import org.ljsearch.*
import org.slf4j.LoggerFactory
/**
 * Created by Pavel on 10/27/2015.
 */
@Canonical
class Comment {

    String link, text, user, subject
    Date date

    boolean isEmpty() {
        return text.isEmpty() && subject.isEmpty()
    }

    void setDate(final String date) {
        if (date.length() == 0) {
            this.date = new Date()
            return
        }
        try {
            this.date = DateUtils.parseDate(date)
        } catch (RuntimeException e) {
            // An unknown date format must not lose the post's other comments
            // (or stop the downloader); the caller falls back to the post date.
            LoggerFactory.getLogger(Comment.class).warn("Unparseable comment date '{}'", date)
            this.date = null
        }
    }


    @Override
    public String toString() {
        return "Comment{" +
                "link='" + link + '\'' +
                ", text='" + text + '\'' +
                ", user='" + user + '\'' +
                ", subject='" + subject + '\'' +
                ", date=" + date +
                '}';
    }
}
