package org.ljsearch.entity

import javax.persistence.Entity
import javax.persistence.Id

/**
 * Created by Pavel on 10/8/2015.
 */
@Entity
class News {
    @Id
    String message
}
