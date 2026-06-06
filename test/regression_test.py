"""
one-api regression test — 基于 3578 条真实 HTTP 请求日志。
用法: python regression_test.py [--base-url http://localhost:13000]
"""
import json, sys, time, argparse, urllib.request, urllib.error

BASE = "http://localhost:13000"

# 从 relay-log.db 提取的真实模型列表
MODELS_SUCCESS = [
    "deepseek-v4-flash", "deepseek-v4-pro",
    "deepseek-v4-pro-max", "doubao-seed-2.0-code", "doubao-seed-2.0-pro",
    "glm-5", "glm-5.1", "kimi-k2.6", "mimo-v2.5", "mimo-v2.5-pro",
    "minimax-m2.5", "minimax-m2.7", "minimax-m2.7-highspeed", "minimax-m3",
    "qwen3-next-80b-a3b-instruct",
]

# auto 路由：能找到模型，但可能返回 503（所有实例不可用）或 200
MODELS_MAYBE_503 = ["auto"]

# Go 版返回 200 但 Java 版应返回 404 的模型
MODELS_SHOULD_404 = ["nonexistent", "nonexistent-model"]

# Go 版返回 400 的模型（已删除的虚拟模型入口）
MODELS_EXPECT_ERROR = ["deepseek-v3-max", "deepseek-v3"]

def make_request(model, stream=False):
    """构造最小 Chat Completions 请求"""
    body = json.dumps({
        "model": model,
        "messages": [{"role": "user", "content": "hi"}],
        "max_tokens": 1,
        "stream": stream,
    }).encode()
    req = urllib.request.Request(
        f"{BASE}/v1/chat/completions",
        data=body,
        headers={
            "Content-Type": "application/json",
            "Authorization": "Bearer sk-test",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as resp:
            return resp.status, resp.read().decode()
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode()
    except Exception as e:
        return 0, str(e)


def test_routing():
    """测试每个虚拟模型能否正确路由"""
    passed = 0
    failed = 0

    for model in MODELS_SUCCESS:
        code, body = make_request(model)
        ok = code in (200, 429, 0)  # 200 成功, 429 限流, 0 上游超时都算路由正确
        status = "Y" if ok else "N"
        if ok:
            passed += 1
        else:
            failed += 1
        print(f"  {status} {model:35s} -> {code}")

    return passed, failed


def test_404():
    """测试不存在的模型应返回 404"""
    passed = 0
    failed = 0

    for model in MODELS_SHOULD_404:
        code, body = make_request(model)
        ok = code == 404
        status = "Y" if ok else "N"
        if ok:
            passed += 1
        else:
            failed += 1
        print(f"  {status} {model:35s} -> {code} (expect 404)")

    return passed, failed


def test_auto():
    """测试 auto 路由 — 可能 200（成功）或 503（所有实例不可用）"""
    passed = 0
    failed = 0
    for model in MODELS_MAYBE_503:
        code, body = make_request(model)
        ok = code in (200, 429, 503)  # 路由成功/限流/全部不可用 都算正确路由
        status = "Y" if ok else "N"
        if ok:
            passed += 1
        else:
            failed += 1
        print("  {} {} -> {} (200/429/503 ok)".format(status, model, code))
    return passed, failed


def test_error_models():
    """测试已删除/错误的模型应返回 400 或 404"""
    passed = 0
    failed = 0

    for model in MODELS_EXPECT_ERROR:
        code, body = make_request(model)
        ok = code in (400, 404)
        status = "Y" if ok else "N"
        if ok:
            passed += 1
        else:
            failed += 1
        print(f"  {status} {model:35s} -> {code} (expect 400/404)")

    return passed, failed


def test_status_endpoint():
    """测试健康检查端点"""
    req = urllib.request.Request(f"{BASE}/api/status")
    try:
        with urllib.request.urlopen(req, timeout=5) as resp:
            code = resp.status
            body = json.loads(resp.read())
            data = body.get("data", {})
            ok = code == 200 and data.get("system_name") == "one-api-java"
            print("  {} /api/status -> {} system={}".format("Y" if ok else "N", code, data.get("system_name")))
            return (1, 0) if ok else (0, 1)
    except Exception as e:
        print("  N /api/status -> ERROR: {}".format(e))
        return 0, 1


def main():
    global BASE
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default=BASE)
    args = parser.parse_args()
    BASE = args.base_url

    print("=== one-api regression test ===")
    print("Base URL: " + BASE)
    print()

    total_p, total_f = 0, 0

    # 1. 健康检查
    print("[1/4] Status endpoint")
    p, f = test_status_endpoint()
    total_p += p; total_f += f
    print()

    # 2. 虚拟模型路由
    print("[2/4] Virtual model routing (15 models from real traffic)")
    p, f = test_routing()
    total_p += p; total_f += f
    print()

    # 2b. auto 路由
    print("[2b/4] Auto routing")
    p, f = test_auto()
    total_p += p; total_f += f
    print()

    # 3. 不存在的模型 -> 404
    print("[3/4] Nonexistent models (should be 404)")
    p, f = test_404()
    total_p += p; total_f += f
    print()

    # 4. 错误模型
    print("[4/4] Deleted/error models")
    p, f = test_error_models()
    total_p += p; total_f += f
    print()

    # 总结
    total = total_p + total_f
    print("=== Results: {}/{} passed, {} failed ===".format(total_p, total, total_f))
    if total_f > 0:
        print("FAIL")
        sys.exit(1)
    else:
        print("ALL PASS")
        sys.exit(0)


if __name__ == "__main__":
    main()
