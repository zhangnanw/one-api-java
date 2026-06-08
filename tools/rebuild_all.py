"""重建入口 + 画像表，与 model-catalog.yaml 对齐。"""
import sqlite3, json, os

db = os.path.expandvars(r'%USERPROFILE%\.one-api\one-api.db')
conn = sqlite3.connect(db)
c = conn.cursor()

c.execute("DELETE FROM model_catalog")

catalogs = [
    ("deepseek-v4-flash",                        ["code","chat"],        131072),
    ("deepseek-v4-pro",                          ["code","chat"],        131072),
    ("kimi-k2.6",                                ["code","chat","vision"], 131072),
    ("kimi-k2.5",                                ["code","chat"],        131072),
    ("minimax-m3",                               ["chat","code","vision"], 131072),
    ("minimax-m2.7",                             ["chat","code"],        131072),
    ("minimax-m2.5",                             ["chat"],               131072),
    ("mimo-v2-flash",                            ["chat","code"],        131072),
    ("mimo-v2-pro",                              ["chat","code"],        131072),
    ("mimo-v2.5",                                ["chat","code","vision"], 131072),
    ("mimo-v2.5-pro",                            ["chat","code","reasoning"], 131072),
    ("doubao-seed-2.0-pro-260215",               ["chat","reasoning"],   131072),
    ("doubao-seed-2.0-code-260215",              ["code","vision"],      131072),
    ("doubao-seed-2.0-lite-260428",              ["chat"],               131072),
    ("doubao-seed-2-0-lite-260215",              ["chat"],               131072),
    ("doubao-seed-2-0-mini-260215",              ["chat"],               131072),
    ("doubao-seed-2-0-mini-260428",              ["chat"],               131072),
    # === 写作模型（选型报告 2026-06-08）===
    ("deepseek-r1",                              ["writing","reasoning","chat"], 131072),
    ("deepseek-v3-0324",                         ["writing","chat"],      131072),
    ("deepseek-v3.2",                            ["writing","chat"],      131072),
    ("moonshot-k2",                              ["writing","chat","vision"], 131072),
    ("step-2-literary",                          ["writing","chat"],      131072),
    ("kimi-k1.5",                                ["writing","chat"],      65536),
    ("tencent-hunyuan",                          ["writing","chat"],      131072),
    ("doubao-pro",                               ["writing","chat"],      32768),
    ("yuewen-miaobi",                            ["writing","chat"],      131072),
    ("qwen-max",                                 ["writing","chat"],      32768),
    # === 国产大模型调查报告 2025H1 补录 ===
    ("qwen2.5-coder",                            ["code","chat","tool_calling"], 131072),
    ("qwen3.5-flash",                            ["chat","long_context"],        1048576),
    ("qwen3.5-plus",                             ["chat","code"],                131072),
    ("glm-4-flash",                              ["chat"],                       131072),
    ("glm-4-plus",                               ["chat","tool_calling"],        131072),
    ("glm-z1-air",                               ["chat","reasoning"],           131072),
    ("doubao-1.5-pro",                           ["chat","vision","low_hallucination"], 262144),
]

for name, caps, ctx in catalogs:
    c.execute("INSERT OR REPLACE INTO model_catalog (name, capabilities, context_window) VALUES (?, ?, ?)",
              (name, json.dumps(caps), ctx))

entries = {
    "deepseek": ["deepseek-v4-flash", "deepseek-v4-pro"],
    "kimi":     ["kimi-k2.6", "kimi-k2.5"],
    "minimax":  ["minimax-m2.7", "minimax-m3", "minimax-m2.5"],
    "mimo":     ["mimo-v2-flash", "mimo-v2-pro", "mimo-v2.5", "mimo-v2.5-pro"],
    "doubao":   ["doubao-seed-2.0-pro-260215", "doubao-seed-2.0-code-260215",
                 "doubao-seed-2.0-lite-260428", "doubao-seed-2-0-lite-260215",
                 "doubao-seed-2-0-mini-260215", "doubao-seed-2-0-mini-260428"],
    "flagship": ["kimi-k2.6", "mimo-v2.5-pro", "minimax-m3",
                 "doubao-seed-2.0-pro-260215", "deepseek-v4-pro"],
}

for name, models in entries.items():
    match = json.dumps({"models": models})
    c.execute("INSERT OR REPLACE INTO virtual_models (name, match) VALUES (?, ?)", (name, match))

conn.commit()

print(f"model_catalog: {len(catalogs)} 条")
print(f"virtual_models: {len(entries)} 入口")
for name, models in entries.items():
    print(f"  {name}: {models}")
