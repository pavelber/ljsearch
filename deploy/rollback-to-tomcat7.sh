#!/bin/sh
# Back to Tomcat 7 with the old WAR (/var/lib/tomcat7/webapps/ROOT.war). The
# index format is the same (Lucene 5.3.1). Liquibase 3.4 recalculates the
# change log checksums the new version upgraded. Run with sudo.
set -e
systemctl stop ljsearch
systemctl disable ljsearch
systemctl enable tomcat7
systemctl start tomcat7
systemctl --no-pager status tomcat7 | head -5
