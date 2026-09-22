#!/usr/bin/env python3
"""Website-only Hermes adapter. No tools, personal memory, filesystem context, or shared sessions.

Run with the installed Hermes Python runtime inside the accompanying systemd sandbox.
Credentials are supplied by a root-owned EnvironmentFile; never logged or returned.
"""
import hmac
import json
import logging
import os
import threading
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SLOT = threading.BoundedSemaphore(1)
TOKEN = os.environ["MAIMAI_SITE_TOKEN"]
MODEL = os.environ.get("MAIMAI_SITE_MODEL", "MiniMax-M3")
CAPABILITIES = {"adapter": "maimai-hermes", "version": 1, "runtime": "Hermes AIAgent",
                "tools": [], "memory": False, "context_files": False, "shared_sessions": False}


def agent_factory():
    from run_agent import AIAgent
    agent = AIAgent(
        base_url=os.environ["MAIMAI_SITE_PROVIDER_URL"], api_key=os.environ["MAIMAI_SITE_PROVIDER_KEY"],
        provider="custom", api_mode="chat_completions", model=MODEL, max_iterations=1, max_tokens=600,
        enabled_toolsets=[], save_trajectories=False, quiet_mode=True, verbose_logging=False,
        skip_context_files=True, load_soul_identity=False, skip_memory=True, skip_background_review=True,
        session_db=None, checkpoints_enabled=False, platform="maimai_site", session_id=str(uuid.uuid4()),
        request_overrides={"tool_choice": "none", "extra_body": {"reasoning_split": True}},
    )
    # Fail closed if a runtime update ever enables tools despite the explicit empty allowlist.
    if agent.tools or agent.valid_tool_names:
        agent.close()
        raise RuntimeError("HERMES_TOOLS_MUST_BE_DISABLED")
    agent._persist_disabled = True
    agent._print_fn = lambda *_args, **_kwargs: None
    agent._dump_api_request_debug = lambda *_args, **_kwargs: None
    # This Hermes runtime counts total attempts, not retries after the first attempt.
    agent._api_max_retries = 1
    return agent


def validate(payload):
    if not isinstance(payload, dict) or payload.get("stream") is True or payload.get("tools") or payload.get("tool_choice", "none") != "none":
        raise ValueError("Only plain read-only chat is supported")
    messages = payload.get("messages")
    if not isinstance(messages, list) or not 2 <= len(messages) <= 14:
        raise ValueError("Invalid messages")
    for index, message in enumerate(messages):
        expected = "system" if index == 0 else "user" if index % 2 else "assistant"
        if not isinstance(message, dict) or message.get("role") != expected or not isinstance(message.get("content"), str):
            raise ValueError("Invalid message role or content")
        if len(message["content"]) > 8000:
            raise ValueError("Message too long")
    if len(messages) % 2 or sum(len(m["content"]) for m in messages) > 22000:
        raise ValueError("Invalid conversation size")
    return messages


class Handler(BaseHTTPRequestHandler):
    server_version = "MaimaiHermes/1"

    def log_message(self, *_args):
        pass  # No user text, auth headers, provider errors, or credential-bearing URLs in logs.

    def send_json(self, status, value):
        content = json.dumps(value, ensure_ascii=False).encode("utf-8")
        try:
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(content)))
            self.end_headers()
            self.wfile.write(content)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def authorized(self):
        return hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + TOKEN)

    def do_GET(self):
        if self.path == "/health":
            self.send_json(200, {"status": "UP", "runtime": "Hermes AIAgent", "toolsEnabled": False})
        elif self.path == "/v1/capabilities" and self.authorized():
            self.send_json(200, CAPABILITIES)
        else:
            self.send_json(404, {"error": "Not found"})

    def do_POST(self):
        if self.path != "/v1/chat/completions":
            self.send_json(404, {"error": "Not found"}); return
        if not self.authorized():
            self.send_json(401, {"error": "Unauthorized"}); return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            if not 1 <= length <= 96000:
                raise ValueError("Invalid request size")
            self.connection.settimeout(5)
            messages = validate(json.loads(self.rfile.read(length)))
        except (ValueError, OSError):
            self.send_json(400, {"error": "Invalid read-only request"}); return
        if not SLOT.acquire(blocking=False):
            self.send_json(429, {"error": "Busy"}); return
        agent = None
        try:
            agent = agent_factory()
            result = agent.run_conversation(user_message=messages[-1]["content"],
                system_message=messages[0]["content"], conversation_history=messages[1:-1] or None)
            answer = result.get("final_response")
            # Hermes failures are not successful advice. Only accept a final plain assistant turn.
            history = result.get("messages", [])
            print(json.dumps({"api_calls": result.get("api_calls"), "failed": result.get("failed"),
                "exit_kind": str(result.get("turn_exit_reason", "unknown")).split("(", 1)[0].split(":", 1)[0][:70]}), flush=True)
            if not isinstance(answer, str) or not answer.strip() or len(answer) > 1800 or "<think" in answer.lower():
                raise RuntimeError("Invalid final answer")
            if any(m.get("tool_calls") or m.get("role") == "tool" for m in history if isinstance(m, dict)):
                raise RuntimeError("Unexpected tool call")
            if result.get("error") or result.get("failed") or "No reply:" in answer or answer.startswith("I apologize, but I encountered"):
                raise RuntimeError("Model failure")
            self.send_json(200, {"id": "hermes-" + uuid.uuid4().hex, "model": "hermes-maizai/" + MODEL,
                "choices": [{"finish_reason": "stop", "message": {"role": "assistant", "content": answer}}]})
        except Exception as error:
            print("Hermes request failed: " + type(error).__name__, flush=True)
            self.send_json(503, {"error": "Hermes temporarily unavailable"})
        finally:
            try:
                if agent is not None:
                    agent.close()
            finally:
                SLOT.release()


if __name__ == "__main__":
    logging.disable(logging.CRITICAL)
    probe = agent_factory()
    probe.close()
    print("Hermes website adapter ready; tools=0; memory=off; context=off", flush=True)
    server = ThreadingHTTPServer(("127.0.0.1", int(os.environ.get("MAIMAI_SITE_PORT", "8643"))), Handler)
    server.daemon_threads = True
    server.serve_forever()
