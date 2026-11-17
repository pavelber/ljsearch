package org.ljsearch.entity

import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

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
