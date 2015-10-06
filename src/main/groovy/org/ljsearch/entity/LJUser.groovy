package org.ljsearch.entity

import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

/**
 * Created by Pavel on 9/29/2015.
 */
@Entity
@Table(name="users")
class LJUser {
    @Id
    String username;
    String password;
}
