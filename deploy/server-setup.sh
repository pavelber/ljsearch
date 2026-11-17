#!/bin/sh
# One-time setup for the Spring Boot 3 / Java 17 version. Run with sudo:
#   sudo sh ~/server-setup.sh
# Installs JDK 17 and the ljsearch service, but does not start it and does not
# touch Tomcat 7: switching over is switch-to-ljsearch.sh.
set -e

JDK_URL=https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse

if [ ! -x /opt/jdk-17/bin/java ]; then
    tmp=$(mktemp -d)
    echo "Downloading Temurin JDK 17 ..."
    curl -fL -o "$tmp/jdk17.tar.gz" "$JDK_URL"
    mkdir -p /opt/jdk-17
    tar -xzf "$tmp/jdk17.tar.gz" -C /opt/jdk-17 --strip-components=1
    rm -rf "$tmp"
fi
/opt/jdk-17/bin/java -version

# the WAR lives here; pavel uploads new versions
install -d -o pavel -g tomcat7 -m 2775 /var/lib/ljsearch
install -d -o tomcat7 -g tomcat7 -m 0755 /var/lib/ljsearch/tmp
install -d -o tomcat7 -g adm -m 0755 /var/log/ljsearch
if [ -f /home/pavel/ROOT.boot3.war ]; then
    install -o pavel -g tomcat7 -m 0644 /home/pavel/ROOT.boot3.war /var/lib/ljsearch/ROOT.war
fi

install -o root -g root -m 0644 /home/pavel/ljsearch.service /etc/systemd/system/ljsearch.service
systemctl daemon-reload
echo "Installed. Not started: run switch-to-ljsearch.sh to move from Tomcat 7."
