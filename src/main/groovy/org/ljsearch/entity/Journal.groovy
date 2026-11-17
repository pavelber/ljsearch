package org.ljsearch.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.Table

/**
 * Created by Pavel on 9/29/2015.
 */
@Entity
@Table(name="journals")
class Journal {
    @Id
    String journal
    @ManyToOne
    @JoinColumn(name="user")
    LJUser user
    Date last
    @Column(name = "private")
    boolean prrivate
}
