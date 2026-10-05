#!/usr/bin/env python3
"""TEST ONLY: fixed vectors and canned answers. Never a replacement for Ollama."""
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
class Handler(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200); self.end_headers(); self.wfile.write(b"test-only")
    def read_body(self):
        # JDK's streaming HTTP client sends JSON with chunked transfer encoding.
        if 'chunked' in self.headers.get('Transfer-Encoding', '').lower():
            chunks = []
            while True:
                size = int(self.rfile.readline().split(b';', 1)[0].strip(), 16)
                if size == 0:
                    while self.rfile.readline().strip():
                        pass
                    break
                chunks.append(self.rfile.read(size))
                self.rfile.read(2)  # trailing CRLF
            return b''.join(chunks)
        return self.rfile.read(int(self.headers.get('Content-Length', 0)))
    def do_POST(self):
        data = json.loads(self.read_body())
        if self.path == '/api/embed':
            inputs = data.get('input', [])
            if isinstance(inputs, str): inputs = [inputs]
            result = {'model': 'test-only', 'embeddings': [[1.0] + [0.0] * 767 for _ in inputs]}
        elif self.path == '/api/chat':
            unsupported = 'salary' in str(data.get('messages', '')).lower()
            draft = {'answerable': not unsupported,
                     'answer': 'I could not find sufficient evidence.' if unsupported else 'Post incident updates every 30 minutes [1].',
                     'citations': [] if unsupported else [1]}
            result = {'model': 'test-only', 'message': {'role': 'assistant', 'content': json.dumps(draft)}, 'done': True, 'done_reason': 'stop'}
        else:
            self.send_error(404); return
        body = json.dumps(result).encode()
        self.send_response(200); self.send_header('Content-Type', 'application/json'); self.send_header('Content-Length', str(len(body)))
        self.end_headers(); self.wfile.write(body)
    def log_message(self, *_): pass
if __name__ == '__main__':
    print('TEST ONLY Ollama protocol stub listening on 127.0.0.1:11435', flush=True)
    ThreadingHTTPServer(('127.0.0.1', 11435), Handler).serve_forever()
