"""Local preview of the UI variants against the real getlj.com data.

Serves the files of this folder and forwards /search and /journals (read-only
JSON endpoints, they never call LiveJournal) to the live server, so the page
looks as it would after deployment without touching the WAR.

    python ui-variants/dev_server.py [port]
"""
import http.server
import os
import sys
import urllib.request

UPSTREAM = "https://getlj.com"
PROXIED = ("/search", "/api/search", "/journals")   # /api/search exists only after the paging backend is deployed
HERE = os.path.dirname(os.path.abspath(__file__))


class Handler(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **kw):
        super().__init__(*a, directory=HERE, **kw)

    def do_GET(self):
        if self.path.split("?")[0] in PROXIED:
            req = urllib.request.Request(UPSTREAM + self.path, headers={"User-Agent": "ljsearch-ui-preview"})
            try:
                with urllib.request.urlopen(req, timeout=60) as r:
                    body, status = r.read(), r.status
            except urllib.error.HTTPError as e:
                body, status = e.read(), e.code
            except Exception as e:  # network error
                body, status = str(e).encode(), 502
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)
        else:
            super().do_GET()


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8766
    print(f"http://localhost:{port}/c-archive.html  (API -> {UPSTREAM})", flush=True)
    http.server.ThreadingHTTPServer(("127.0.0.1", port), Handler).serve_forever()
