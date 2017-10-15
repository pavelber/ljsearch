package org.ljsearch.comments

import groovy.transform.Canonical

import org.ljsearch.*
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
        if (date.length() == 0)
            this.date = new Date()
        else
            this.date = DateUtils.parseDate(date)
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
