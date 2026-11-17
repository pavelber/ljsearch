package org.ljsearch

import groovy.sql.Sql
import liquibase.integration.spring.SpringLiquibase
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.datasource.SingleConnectionDataSource
import spock.lang.Specification

/**
 * The production database was migrated by Liquibase 3.4.1, which stored the
 * change log path as "classpath:/db/changelog/db.changelog-master.xml". The
 * path is part of a change set's identity: if the current Liquibase saw the
 * change sets under another name it would run them again (CREATE TABLE users
 * on a live database). This runs it the way Spring Boot does on a copy of the
 * production DATABASECHANGELOG (2026-11-17) and checks that nothing runs.
 */
class LiquibaseChangelogTest extends Specification {

    static final String FILE = 'classpath:/db/changelog/db.changelog-master.xml'

    static final List<List> RAN = [
            ['initial changeset', '2015-11-16 01:07:25', 1, '7:920e40028fccc823ca5db2a5c21ba566', 'createTable (x2)'],
            ['news', '2015-11-16 01:07:25', 2, '7:b2cf70c9b45eb7a1dfe41883fb7bb832', 'createTable'],
            ['comments timestamp', '2015-11-16 01:07:25', 3, '7:2bc0416e600872bcaac80fcb8c5d8ec7', 'addColumn'],
            ['add records table', '2015-11-23 14:14:09', 4, '7:356ab79829291073b6e5ff76745ae7ea', 'createTable'],
            ['Create index', '2015-11-24 11:57:22', 5, '7:35f1dd1503f0b87f2445dbb67b75f33f', 'createIndex'],
            ['charset', '2015-12-04 21:43:47', 6, '7:f9ebba0f62d675cab984a59c67401f0e', 'sql'],
            ['add private journals', '2017-01-05 15:05:52', 7, '7:f1bdb458bca0cfca39870fc13d9f6625', 'addColumn'],
    ]

    def "the change sets applied by Liquibase 3.4.1 are not run again"() {
        given:
        def ds = new SingleConnectionDataSource('jdbc:h2:mem:lb;MODE=MySQL', 'sa', '', true)
        def sql = new Sql(ds)
        sql.execute('''CREATE TABLE DATABASECHANGELOG (
                ID varchar(255) NOT NULL, AUTHOR varchar(255) NOT NULL, FILENAME varchar(255) NOT NULL,
                DATEEXECUTED datetime NOT NULL, ORDEREXECUTED int NOT NULL, EXECTYPE varchar(10) NOT NULL,
                MD5SUM varchar(35), DESCRIPTION varchar(255), COMMENTS varchar(255), TAG varchar(255),
                LIQUIBASE varchar(20), CONTEXTS varchar(255), LABELS varchar(255))''')
        sql.execute('CREATE TABLE DATABASECHANGELOGLOCK (ID int NOT NULL PRIMARY KEY, LOCKED boolean NOT NULL, ' +
                'LOCKGRANTED datetime, LOCKEDBY varchar(255))')
        sql.execute('INSERT INTO DATABASECHANGELOGLOCK VALUES (1, false, null, null)')
        RAN.each { r ->
            sql.execute('INSERT INTO DATABASECHANGELOG (ID, AUTHOR, FILENAME, DATEEXECUTED, ORDEREXECUTED, ' +
                    "EXECTYPE, MD5SUM, DESCRIPTION, COMMENTS, LIQUIBASE) VALUES (?, 'Pavel', ?, ?, ?, 'EXECUTED', ?, ?, '', '3.4.1')",
                    [r[0], FILE, r[1], r[2], r[3], r[4]])
        }

        when:
        def liquibase = new SpringLiquibase(dataSource: ds, changeLog: FILE, resourceLoader: new DefaultResourceLoader())
        liquibase.afterPropertiesSet()

        then:
        sql.firstRow('SELECT count(*) AS n FROM DATABASECHANGELOG').n == RAN.size()
        sql.rows("SELECT table_name FROM information_schema.tables WHERE table_schema = 'PUBLIC' AND lower(table_name) IN ('users', 'journals', 'news', 'records')").isEmpty()

        cleanup:
        sql?.close()
        ds?.destroy()
    }
}
