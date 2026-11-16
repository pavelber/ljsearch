/*
 * Shared logic for the LJ Search UI variants. No dependencies, no build step.
 *
 * Talks to the existing backend unchanged:
 *   GET /journals                         -> [{journal, last, ...}]
 *   GET /search?term&journal&poster&year&type
 *                                         -> [{title, journal, poster, url, text, type, date}]
 * Keeps the old URL parameters (keywords, journal, year, poster, type, private),
 * so existing links to getlj.com keep working.
 *
 * When the backend is not reachable (page opened from disk) it falls back to
 * demo data so the design can be judged locally.
 */
(function () {
  'use strict';

  var MONTHS = ['января', 'февраля', 'марта', 'апреля', 'мая', 'июня', 'июля',
    'августа', 'сентября', 'октября', 'ноября', 'декабря'];
  var MONTHS_SHORT = ['янв', 'фев', 'мар', 'апр', 'мая', 'июн', 'июл', 'авг', 'сен', 'окт', 'ноя', 'дек'];

  function fmtDate(ms, short) {
    if (ms == null) return '';
    var d = new Date(ms);
    return d.getDate() + ' ' + (short ? MONTHS_SHORT : MONTHS)[d.getMonth()] + ' ' + d.getFullYear();
  }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  /* The search snippet is HTML from the highlighter. Keep only text, <mark> and
     line breaks; everything else (scripts, images, links) is dropped. */
  function safeSnippet(html) {
    var tpl = document.createElement('template');
    tpl.innerHTML = html || '';
    var out = document.createDocumentFragment();
    (function walk(node, target) {
      node.childNodes.forEach(function (n) {
        if (n.nodeType === 3) target.appendChild(document.createTextNode(n.nodeValue));
        else if (n.nodeType === 1) {
          var tag = n.tagName;
          if (tag === 'MARK' || tag === 'B') {
            var m = document.createElement('mark');
            m.textContent = n.textContent;
            target.appendChild(m);
          } else if (tag === 'BR') target.appendChild(document.createTextNode(' '));
          else if (tag !== 'SCRIPT' && tag !== 'STYLE') walk(n, target);
        }
      });
    })(tpl.content, out);
    return out;
  }

  /* The index stores "<no title>" for untitled entries (most comments). */
  function cleanTitle(t) {
    t = (t || '').trim();
    return /^<?no title>?$/i.test(t) ? '' : t;
  }

  function journalUrl(name) {
    if (!name) return '#';
    // community/user names with "_" live on "-" subdomains
    return 'https://' + String(name).replace(/_/g, '-') + '.livejournal.com/';
  }

  function readParams() {
    var p = new URLSearchParams(location.search);
    function g(k) {
      var v = p.get(k);
      return (v == null || v === 'null' || v === 'undefined' || v === 'NaN') ? '' : v;
    }
    return {
      keywords: g('keywords'), journal: g('journal'), year: g('year'),
      poster: g('poster'), type: p.has('type') ? g('type') : 'Post', priv: g('private')
    };
  }

  function writeParams(q) {
    var p = new URLSearchParams();
    if (q.keywords) p.set('keywords', q.keywords);
    if (q.journal) p.set('journal', q.journal);
    if (q.year) p.set('year', q.year);
    if (q.poster) p.set('poster', q.poster);
    p.set('type', q.type || '');
    if (q.priv) p.set('private', q.priv);
    history.replaceState(null, '', location.pathname + '?' + p.toString());
  }

  var demoMode = false;
  /* demo data only for local previews, never on the real site */
  var demoAllowed = location.protocol === 'file:' || /^(localhost|127\.0\.0\.1)$/.test(location.hostname);

  function getJSON(url) {
    if (location.protocol === 'file:') return Promise.reject(new Error('file'));
    return fetch(url, { headers: { Accept: 'application/json' } }).then(function (r) {
      if (!r.ok) throw new Error('HTTP ' + r.status);
      return r.json();
    });
  }

  function loadJournals(priv) {
    if (priv) return Promise.resolve([{ journal: priv, last: null }]);
    return getJSON('/journals').catch(function (e) {
      if (!demoAllowed) return [];   // search still works, only the journal list is empty
      demoMode = true;
      return DEMO_JOURNALS;
    }).then(function (list) {
      return list.filter(function (j) { return j.journal; })
        .sort(function (a, b) { return a.journal.localeCompare(b.journal); });
    });
  }

  function search(q) {
    var url = '/search?term=' + encodeURIComponent(q.keywords || '') +
      '&journal=' + encodeURIComponent(q.journal || '') +
      '&poster=' + encodeURIComponent(q.poster || '') +
      '&type=' + encodeURIComponent(q.type || '') +
      '&year=' + encodeURIComponent(q.year || '') +
      '&private=' + encodeURIComponent(q.priv || '');
    return getJSON(url).catch(function (e) {
      if (!demoMode) throw e;
      return demoSearch(q);
    });
  }

  /* Paged search: {total, next, results, years, posts, comments}.
     after = "next" of the previous page; stats only for the first page. */
  function searchPage(q, after, stats, limit) {
    var url = '/api/search?term=' + encodeURIComponent(q.keywords || '') +
      '&journal=' + encodeURIComponent(q.journal || '') +
      '&poster=' + encodeURIComponent(q.poster || '') +
      '&type=' + encodeURIComponent(q.type || '') +
      '&year=' + encodeURIComponent(q.year || '') +
      '&limit=' + (limit || 30) + (stats ? '&stats=true' : '') +
      (after ? '&after=' + encodeURIComponent(after) : '');
    return getJSON(url).catch(function (e) {
      if (!demoMode) throw e;
      return demoSearch(q).then(function (rows) {
        var start = after ? +after : 0, n = limit || 30, page = { total: rows.length,
          results: rows.slice(start, start + n), next: start + n < rows.length ? String(start + n) : null };
        if (stats) {
          page.years = {}; page.posts = 0; page.comments = 0;
          rows.forEach(function (r) {
            var y = new Date(r.date).getFullYear(); page.years[y] = (page.years[y] || 0) + 1;
            if (r.type === 'Post') page.posts++; else page.comments++;
          });
        }
        return page;
      });
    });
  }

  function years() {
    var ys = [];
    for (var y = new Date().getFullYear(); y >= 2001; y--) ys.push(y);
    return ys;
  }

  /* ---------- demo data ---------- */
  var DEMO_JOURNALS = [
    { journal: 'bvallejo', last: Date.parse('2026-11-15') },
    { journal: 'lenay', last: Date.parse('2026-11-14') },
    { journal: 'ru_israel', last: Date.parse('2026-11-15') },
    { journal: 'evrei', last: Date.parse('2026-11-12') },
    { journal: 'oldtelaviv', last: Date.parse('2026-10-30') },
    { journal: 'ru_cats', last: Date.parse('2026-11-01') },
    { journal: 'kitchen_ru', last: Date.parse('2026-11-10') }
  ];
  var DEMO_POSTS = [
    ['Поездка в Иерусалим: заметки на полях', 'ru_israel', 'lenay', 'Post', '2024-04-12',
      'Утром выехали из Тель-Авива, к обеду были у Яффских ворот. <mark>Иерусалим</mark> встретил ветром и запахом свежего хлеба. Долго бродили по Старому городу…'],
    ['Re: Где купить нормальный кофе?', 'ru_israel', 'bvallejo', 'Comment', '2023-11-02',
      'Попробуйте маленькую обжарку у рынка, они варят отличный <mark>кофе</mark> и продают зерно на вес. Только приходите до полудня.'],
    ['Старые фотографии улицы Алленби', 'oldtelaviv', 'bvallejo', 'Post', '2019-07-21',
      'Нашёл в архиве подборку снимков 1930-х. На третьей фотографии виден тот самый <mark>кофе</mark>йный киоск, о котором писали в комментариях к прошлому посту.'],
    ['Кот и коробка: продолжение', 'ru_cats', 'lenay', 'Post', '2021-02-14',
      'Коробку купили большую, но кот выбрал маленькую из-под <mark>кофе</mark>. Прилагаю двенадцать доказательств.'],
    ['Пирог с яблоками без яиц', 'kitchen_ru', 'lenay', 'Post', '2017-10-05',
      'Рецепт проверен трижды. Тесто замешиваем на <mark>кофе</mark> вместо молока — получается тёмный, влажный пирог.'],
    ['Re: Пирог с яблоками без яиц', 'kitchen_ru', 'bvallejo', 'Comment', '2017-10-06',
      'Испекла по вашему рецепту, только <mark>кофе</mark> взяла растворимый. Всё равно вкусно, спасибо!'],
    ['Про память и архивы', 'evrei', 'bvallejo', 'Post', '2012-05-30',
      'Бабушкины письма пролежали в шкафу сорок лет. Перебирая их, понял, что <mark>память</mark> — это не то, что помнишь, а то, что решил сохранить.'],
    ['Итоги года', 'bvallejo', 'bvallejo', 'Post', '2009-12-31',
      'Год был странный. Переехал, сменил работу, научился варить <mark>кофе</mark> в турке. Всех с наступающим!'],
    ['Re: Итоги года', 'bvallejo', 'lenay', 'Comment', '2010-01-01',
      'С Новым годом! Турка — это серьёзно, жду приглашения на <mark>кофе</mark>.'],
    ['Первая запись', 'lenay', 'lenay', 'Post', '2004-03-08',
      'Решила завести дневник. Посмотрим, сколько продержусь. Пока пью <mark>кофе</mark> и смотрю в окно.']
  ];
  function demoSearch(q) {
    var rows = DEMO_POSTS.map(function (r, i) {
      return { title: r[0], journal: r[1], poster: r[2], type: r[3], date: Date.parse(r[4]),
        text: r[5], url: journalUrl(r[1]) + (100000 + i * 731) + '.html' };
    }).filter(function (r) {
      return (!q.journal || r.journal === q.journal) &&
        (!q.poster || r.poster === q.poster) &&
        (!q.type || r.type === q.type) &&
        (!q.year || new Date(r.date).getFullYear() === +q.year);
    }).sort(function (a, b) { return b.date - a.date; });
    return new Promise(function (res) { setTimeout(function () { res(rows); }, 350); });
  }

  window.LJS = {
    fmtDate: fmtDate, esc: esc, cleanTitle: cleanTitle, safeSnippet: safeSnippet, journalUrl: journalUrl,
    readParams: readParams, writeParams: writeParams, loadJournals: loadJournals,
    search: search, searchPage: searchPage, years: years, isDemo: function () { return demoMode; }
  };
})();
