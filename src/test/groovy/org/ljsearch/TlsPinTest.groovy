package org.ljsearch

import spock.lang.Specification

/**
 * LJ does not answer TLS 1.3 handshakes (2026-11-17), so the app must offer
 * TLS 1.2 only unless the command line says otherwise.
 */
class TlsPinTest extends Specification {

    String saved

    def setup() {
        saved = System.getProperty(Application.TLS_PROTOCOLS)
    }

    def cleanup() {
        if (saved == null) {
            System.clearProperty(Application.TLS_PROTOCOLS)
        } else {
            System.setProperty(Application.TLS_PROTOCOLS, saved)
        }
    }

    def "TLS 1.2 is set when nothing is given"() {
        given:
        System.clearProperty(Application.TLS_PROTOCOLS)

        when:
        Application.pinTls12()

        then:
        System.getProperty(Application.TLS_PROTOCOLS) == "TLSv1.2"
    }

    def "an explicit setting is kept"() {
        given:
        System.setProperty(Application.TLS_PROTOCOLS, "TLSv1.2,TLSv1.3")

        when:
        Application.pinTls12()

        then:
        System.getProperty(Application.TLS_PROTOCOLS) == "TLSv1.2,TLSv1.3"
    }
}
