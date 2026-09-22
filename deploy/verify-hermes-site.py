#!/usr/bin/env python3
"""Local-only adapter readiness and real inference check. Never emits credentials."""
import json
import time
import urllib.request
from pathlib import Path

settings = dict(line.split("=", 1) for line in Path("/etc/maimai/hermes-site.env").read_text().splitlines() if "=" in line)
headers = {"Authorization": "Bearer " + settings["MAIMAI_SITE_TOKEN"], "Content-Type": "application/json"}
request = urllib.request.Request("http://127.0.0.1:8643/v1/capabilities", headers=headers)
deadline = time.monotonic() + 30
while True:
    try:
        with urllib.request.urlopen(request, timeout=2) as response:
            capabilities = json.load(response)
        break
    except (OSError, ValueError):
        if time.monotonic() >= deadline:
            raise RuntimeError("Hermes did not become ready within 30 seconds") from None
        time.sleep(1)
assert capabilities["tools"] == [] and not capabilities["memory"] and not capabilities["context_files"]
start = time.monotonic()
payload = {"model": "hermes-maizai/" + settings["MAIMAI_SITE_MODEL"], "stream": False, "tool_choice": "none",
           "messages": [{"role": "system", "content": "你是麦麦二手的麦仔，只有建议能力，没有工具。用一句中文回答，不输出Markdown。不编造已经执行的操作。"},
                        {"role": "user", "content": "这笔订单使用模拟支付，金额100元，付款后是否会真实扣款？"}]}
request = urllib.request.Request("http://127.0.0.1:8643/v1/chat/completions", data=json.dumps(payload).encode(), headers=headers)
with urllib.request.urlopen(request, timeout=65) as response:
    result = json.load(response)
answer = result["choices"][0]["message"]["content"]
assert answer.strip() and not result["choices"][0]["message"].get("tool_calls")
assert "No reply:" not in answer and "模拟" in answer and ("不会" in answer or "不发生" in answer or "不" in answer)
print(json.dumps({"capabilities": capabilities, "seconds": round(time.monotonic() - start, 2), "answer": answer}, ensure_ascii=False))
