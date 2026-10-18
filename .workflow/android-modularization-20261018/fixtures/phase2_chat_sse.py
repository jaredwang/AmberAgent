#!/usr/bin/env python3
"""Task-local OpenAI SSE fixture for ModularizationChatRuntimeDeviceTest.

Never proxies traffic. Main requests are selected by a UUID prompt marker;
auxiliary model calls receive an immediate local response and cannot consume gates.
"""
import argparse
import json
import re
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse

MARKER = re.compile(r"P2_CASE_([0-9a-f-]{36})_(FIRST|FOLLOWUP_1|FOLLOWUP_2|ASK)")
QUESTION = "下一步先检查哪一条本地运行链路？"
OPTIONS = ["先检查排队消息顺序并保留当前草稿", "先检查停止后的恢复入口与审批状态"]
LOCK = threading.RLock()
CASES = {}


def case_state(case_id):
    with LOCK:
        return CASES.setdefault(case_id, {"requests": [], "gates": {}, "errors": []})


def text_content(value):
    if isinstance(value, str):
        return value
    if isinstance(value, list):
        return "\n".join(text_content(item) for item in value)
    if isinstance(value, dict):
        return text_content(value.get("text", value.get("content", "")))
    return ""


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, fmt, *args):
        print(json.dumps({"http": fmt % args}, ensure_ascii=False), flush=True)

    def respond(self, code, payload):
        body = json.dumps(payload, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = urlparse(self.path).path
        if path == "/health":
            return self.respond(200, {"fixture": "amber-p2-local-sse", "version": 1})
        match = re.fullmatch(r"/control/([0-9a-f-]{36})/snapshot", path)
        if match:
            state = case_state(match.group(1))
            with LOCK:
                return self.respond(200, {"requests": state["requests"], "errors": state["errors"]})
        if path == "/v1/models":
            return self.respond(200, {"object": "list", "data": []})
        self.respond(404, {"error": "Unknown local fixture endpoint"})

    def do_POST(self):
        path = urlparse(self.path).path
        payload = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))) or b"{}")
        control = re.fullmatch(r"/control/([0-9a-f-]{36})/release/(FIRST|FOLLOWUP_1|FOLLOWUP_2|all)", path)
        if control:
            state = case_state(control.group(1))
            with LOCK:
                gates = [control.group(2)] if control.group(2) != "all" else ["FIRST", "FOLLOWUP_1", "FOLLOWUP_2"]
                for key in gates:
                    state["gates"].setdefault(key, threading.Event()).set()
            return self.respond(200, {"released": gates})
        if path != "/v1/chat/completions":
            return self.respond(404, {"error": "Only local chat/completions is scripted"})
        model = payload.get("model", "")
        if model.startswith("p2-runtime-aux"):
            return self.answer(payload, "Local fixture\nReview the queue\nReview the answer")
        if not model.startswith("p2-runtime-main"):
            return self.respond(400, {"error": "Unexpected model; fixture never forwards requests"})
        messages = payload.get("messages", [])
        last_user = next((text_content(m.get("content")) for m in reversed(messages) if m.get("role") == "user"), "")
        marker = MARKER.search(last_user)
        if not marker:
            return self.respond(400, {"error": "Main request has no task-owned case marker"})
        case_id, phase = marker.groups()
        tool_id = "p2-ask-" + case_id
        tool_result = next((text_content(m.get("content")) for m in reversed(messages)
                            if m.get("role") == "tool" and m.get("tool_call_id") == tool_id), None)
        if phase == "ASK" and tool_result is not None:
            phase = "ANSWERED"
        state = case_state(case_id)
        record = {"phase": phase, "model": model, "last_user": last_user,
                  "tool_result": tool_result, "tool_names": [t.get("function", {}).get("name") for t in payload.get("tools", [])],
                  "started_at": time.time(), "finished": False, "disconnected": False}
        with LOCK:
            state["requests"].append(record)
        print(json.dumps({"case": case_id, "request": record}, ensure_ascii=False), flush=True)
        if phase == "ASK":
            if "ask_user" not in record["tool_names"]:
                with LOCK:
                    state["errors"].append("Production request did not expose ask_user")
                return self.respond(400, {"error": "Production request did not expose ask_user"})
            tool = {"id": tool_id, "type": "function", "function": {
                "name": "ask_user", "arguments": json.dumps({"questions": [{"id": "p2-choice", "question": QUESTION,
                    "selection_type": "single", "options": OPTIONS}]}, ensure_ascii=False)}}
            return self.answer(payload, None, tool=tool, record=record)
        if phase == "ANSWERED":
            return self.answer(payload, "P2_ANSWERED_CASE_" + case_id, record=record)
        return self.answer(payload, "P2_STREAM_" + phase + "_CASE_" + case_id,
                           gate=state["gates"].setdefault(phase, threading.Event()),
                           final_text=" P2_FINISHED_" + phase + "_CASE_" + case_id, record=record)

    def answer(self, request, text, tool=None, gate=None, final_text=None, record=None):
        model = request.get("model", "p2-runtime-aux")
        finish = "tool_calls" if tool else "stop"
        if not request.get("stream"):
            message = {"role": "assistant", "content": text}
            if tool:
                message["tool_calls"] = [tool]
            self.respond(200, {"id": "p2-local", "object": "chat.completion", "model": model,
                "choices": [{"index": 0, "message": message, "finish_reason": finish}],
                "usage": {"prompt_tokens": 10, "completion_tokens": 5, "total_tokens": 15}})
            if record is not None:
                with LOCK:
                    record["finished"] = True
            return
        self.send_response(200)
        self.send_header("Content-Type", "text/event-stream; charset=utf-8")
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        self.close_connection = True
        try:
            delta = {"role": "assistant"}
            if tool:
                delta["tool_calls"] = [{"index": 0, **tool}]
            else:
                delta["content"] = text
            self.chunk(model, delta)
            if gate is not None:
                deadline = time.monotonic() + 120
                while not gate.wait(0.2):
                    if time.monotonic() > deadline:
                        raise TimeoutError("Test did not release its explicit stream gate")
                    self.wfile.write(b": local fixture gate\n\n")
                    self.wfile.flush()
            if final_text:
                self.chunk(model, {"content": final_text})
            self.chunk(model, {}, finish)
            self.wfile.write(b"data: [DONE]\n\n")
            self.wfile.flush()
            if record is not None:
                with LOCK:
                    record["finished"] = True
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError):
            if record is not None:
                with LOCK:
                    record["disconnected"] = True
        except TimeoutError as error:
            if record is not None:
                with LOCK:
                    record["error"] = str(error)
        finally:
            if record is not None:
                with LOCK:
                    record["ended_at"] = time.time()

    def chunk(self, model, delta, finish=None):
        payload = {"id": "p2-local", "object": "chat.completion.chunk", "created": int(time.time()), "model": model,
                   "choices": [{"index": 0, "delta": delta, "finish_reason": finish}]}
        self.wfile.write(("data: " + json.dumps(payload, ensure_ascii=False) + "\n\n").encode())
        self.wfile.flush()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=18766)
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    server.daemon_threads = True
    print(json.dumps({"fixture": "amber-p2-local-sse", "listening": server.server_address}), flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
