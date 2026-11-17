#!/bin/sh
# Moves the site from Tomcat 7 to the ljsearch service (both use port 8080
# and the same index, so they never run together). Run with sudo.
# Back: rollback-to-tomcat7.sh.
set -e
systemctl stop tomcat7
systemctl disable tomcat7
systemctl enable ljsearch
systemctl start ljsearch
systemctl --no-pager status ljsearch | head -5
