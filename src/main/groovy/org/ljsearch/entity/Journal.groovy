package org.ljsearch.entity

import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.JoinColumn
import javax.persistence.ManyToOne
import javax.persistence.Table

/**
 * Created by Pavel on 9/29/2015.
 */
@Entity
@Table(name="journals")
class Journal {
    @Id
    String journal;
    @ManyToOne
    @JoinColumn(name="user")
    LJUser user;
    Date last;
    Date lastcomment;
}
