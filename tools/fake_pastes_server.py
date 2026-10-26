#!/usr/bin/env python3
# gw_paste98 - Java 1.3+ / Windows 95 and 98 client for the Glitch Works pastes service
# Copyright (C) 2026 Glitch Works, LLC
# Licensed under the GNU GPL v3 or later. See the LICENSE file for details.
"""
A stand-in for the Glitch Works pastes service (glitchwrks/pastes) for
testing the client without touching production. It mimics paste_app.rb:

  POST /api/pastes   HTTP Basic Auth; JSON {"name": 8 chars, "content": str}
                     201 + empty body        created
                     401 "Not authorized\\n"  bad credentials
                     422 "Unprocessable JSON entity"   fails the JSON schema
                     422 {"name":["has already been taken"]}   name exists
                     422 {"content":["can't be blank"]}        empty content
  GET /<name>        200 text/plain content, or 404 "Not found."

Content is stored as latin1, like the real MariaDB column: characters
outside Latin-1 become '?'.

Usage:
  fake_pastes_server.py --cert CERT --key KEY [--port 9443] [--user U --password P]
                        [--collide N] [--log FILE]

--collide N answers the first N POSTs with "name already taken".
--log FILE appends one JSON line per request (the Authorization header is
           recorded only as valid/invalid).
"""
import argparse
import base64
import json
import ssl
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

STORE = {}
LOCK = threading.Lock()
STATE = {"collide": 0}
ARGS = None


def log(entry):
    if ARGS.log:
        with LOCK, open(ARGS.log, "a") as f:
            f.write(json.dumps(entry) + "\n")


def schema_ok(doc):
    if not isinstance(doc, dict):
        return False
    if set(doc) - {"name", "content"}:
        return False  # additionalProperties: false
    if len(doc) < 2:
        return False  # minProperties: 2
    name = doc.get("name")
    if not isinstance(name, str) or len(name) != 8:
        return False
    if "content" in doc and not isinstance(doc["content"], str):
        return False
    return True


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        pass

    def reply(self, status, body=b"", ctype="text/plain;charset=utf-8", extra=None):
        self.send_response(status)
        self.send_header("Content-Type", ctype)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Connection", "close")
        for k, v in (extra or {}).items():
            self.send_header(k, v)
        self.end_headers()
        self.wfile.write(body)
        self.close_connection = True

    def do_GET(self):
        name = self.path.lstrip("/")
        with LOCK:
            content = STORE.get(name)
        log({"method": "GET", "path": self.path, "found": content is not None})
        if content is None:
            self.reply(404, b"Not found.")
        else:
            self.reply(200, content.encode("latin-1"))

    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length)
        auth_ok = False
        auth = self.headers.get("Authorization", "")
        if auth.startswith("Basic "):
            try:
                user, _, pw = base64.b64decode(auth[6:]).decode("utf-8").partition(":")
                auth_ok = (user == ARGS.user and pw == ARGS.password)
            except Exception:
                auth_ok = False
        entry = {"method": "POST", "path": self.path, "auth_ok": auth_ok,
                 "content_type": self.headers.get("Content-Type"), "length": length,
                 "user_agent": self.headers.get("User-Agent"), "body_is_ascii": all(b < 0x80 for b in raw)}
        if self.path != "/api/pastes":
            entry["status"] = 404
            log(entry)
            self.reply(404, b"Not found.")
            return
        if not auth_ok:
            entry["status"] = 401
            log(entry)
            self.reply(401, b"Not authorized\n", extra={"WWW-Authenticate": 'Basic realm="Restricted Area"'})
            return
        try:
            doc = json.loads(raw.decode("utf-8"))
        except Exception:
            doc = None
        if doc is None or not schema_ok(doc):
            entry["status"] = 422
            log(entry)
            self.reply(422, b"Unprocessable JSON entity")
            return
        entry["name"] = doc["name"]
        with LOCK:
            collide = STATE["collide"] > 0
            if collide:
                STATE["collide"] -= 1
            taken = collide or doc["name"] in STORE
            if not taken and doc.get("content"):
                STORE[doc["name"]] = doc["content"].encode("latin-1", "replace").decode("latin-1")
        if taken:
            entry["status"] = 422
            log(entry)
            self.reply(422, b'{"name":["has already been taken"]}', "application/json")
            return
        if not doc.get("content"):
            entry["status"] = 422
            log(entry)
            self.reply(422, b'{"content":["can\'t be blank"]}', "application/json")
            return
        entry["status"] = 201
        log(entry)
        self.reply(201)


def main():
    global ARGS
    p = argparse.ArgumentParser()
    p.add_argument("--cert", required=True)
    p.add_argument("--key", required=True)
    p.add_argument("--port", type=int, default=9443)
    p.add_argument("--user", default="glitch")
    p.add_argument("--password", default="s3cret: pass")
    p.add_argument("--collide", type=int, default=0)
    p.add_argument("--log")
    ARGS = p.parse_args()
    STATE["collide"] = ARGS.collide

    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.minimum_version = ssl.TLSVersion.TLSv1_2
    ctx.load_cert_chain(ARGS.cert, ARGS.key)
    server = ThreadingHTTPServer(("127.0.0.1", ARGS.port), Handler)
    server.socket = ctx.wrap_socket(server.socket, server_side=True)
    print("fake pastes server on https://127.0.0.1:%d/" % ARGS.port, flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
