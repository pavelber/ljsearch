package org.ljsearch.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id

/**
 * Created by Pavel on 10/8/2015.
 */
@Entity
class News {
    @Id
    String message
}
