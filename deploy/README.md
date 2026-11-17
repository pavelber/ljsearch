# Запуск на сервере (Java 17, Spring Boot 3)

Spring Boot 3 требует Servlet 6, а Tomcat 7 на сервере его не поддерживает.
Поэтому приложение запускается само, со встроенным Tomcat 10.1, как сервис
systemd `ljsearch`. Порт, индекс и пользователь те же, что у Tomcat 7: 8080,
`/usr/share/tomcat7/ljsearch`, `tomcat7`. Apache по-прежнему проксирует на
`localhost:8080`.

| Что | Где |
|---|---|
| JDK 17 (Temurin) | `/opt/jdk-17` |
| WAR | `/var/lib/ljsearch/ROOT.war` (владелец pavel) |
| Лог | `/var/log/ljsearch/ljsearch.log`, также `journalctl -u ljsearch` |
| Сервис | `/etc/systemd/system/ljsearch.service` |

Сборка: `./gradlew bootWar` создаёт `build/libs/ROOT.war`, нужен JDK 17 или новее.
Тесты, которые обращаются к LiveJournal, из сборки исключены.

## Грабли, на которые уже наступили (2026-11-17)

Каждая из них проявилась при первом переходе. Не убирай защиту, не прочитав, зачем она.

1. **LJ не отвечает на TLS 1.3.** Java 17 по умолчанию начинает рукопожатие с TLS 1.3,
   а LJ через туннель его просто не отвечает. Каждый вызов XML-RPC висел до
   `Read timed out` (2 минуты), после чего скачивание засыпало на 20 минут, и так
   по кругу. Java 8 знала только TLS 1.2, поэтому раньше этого не было.
   Защита: `-Djdk.tls.client.protocols=TLSv1.2` в `ljsearch.service`, а также
   `Application.pinTls12()`, который ставит то же самое, если флага нет.
   Проверяет `TlsPinTest`.
   Проверка вручную, один запрос без логина:
   `java -DsocksProxyHost=127.0.0.1 -DsocksProxyPort=1338 -Djdk.tls.client.protocols=TLSv1.2 ...`
   к `https://www.livejournal.com/`. С TLS 1.3 тот же запрос зависает на рукопожатии.
2. **Уровень байткода.** Groovy берёт его из `java.targetCompatibility`, а не из
   `options.release`. Один WAR, собранный на JDK 21, упал на сервере с
   `UnsupportedClassVersionError`. Защита: задача `checkWarBytecode` после `bootWar`
   валит сборку, если какой-то класс новее Java 17.
3. **SOCKS-прокси до LJ** раньше задавался в `/etc/tomcat7/catalina.properties`, а не
   в коде. Без `-DsocksProxyHost`/`-DsocksProxyPort` в сервисе запросы пошли бы
   в LJ напрямую с IP сервера.
4. **Тесты с логином в LJ.** `XMLRPCClientImplTest` и соседние тесты логинятся в LJ
   (пароль в тесте пустой). Один их прогон получил бан IP за login failure rate.
   Они исключены в `build.gradle`; не включай их обратно в обычную сборку.
5. **Пробный запуск проверяет не всё.** Экземпляр на порту 18080 с пустым индексом
   и `ljsearch.downloads.enabled=false` проверяет старт, базу и страницы, но не
   обращается к LJ. После переключения дождись в логе первой строки
   `Got N entries` и только потом считай переход законченным. Если строк нет,
   а есть таймауты, откатывайся.

## Первый переход

1. Скопировать в домашний каталог pavel `ROOT.war` под именем
   `ROOT.boot3.war`, а также `ljsearch.service` и все `*.sh` отсюда.
2. `sudo sh ~/server-setup.sh`: ставит JDK и сервис, ничего не запускает.
3. `sudo sh ~/switch-to-ljsearch.sh`: останавливает Tomcat 7 и запускает `ljsearch`.
4. Если что-то не так, `sudo sh ~/rollback-to-tomcat7.sh` возвращает старую версию.
   Индекс тот же, формат Lucene не менялся.

## Обновление

Скопировать новый WAR на место `/var/lib/ljsearch/ROOT.war`, затем
`sudo systemctl restart ljsearch`. Автоматического передеплоя, как в Tomcat,
здесь нет. Каждый перезапуск заново логинится во все журналы, поэтому
перезапускать без нужды не стоит.
