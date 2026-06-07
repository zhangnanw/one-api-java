import sqlite3, json, os

db = os.path.expandvars(r'%USERPROFILE%\.one-api\one-api.db')
conn = sqlite3.connect(db)
c = conn.cursor()

# 1. 清空画像表
c.execute("DELETE FROM model_catalog")

catalogs = [
    ("deepseek-v4-flash",       ["code","chat"],        131072),
    ("deepseek-v4-pro",         ["code","chat"],        131072),
    ("deepseek-v4-pro-260425",  ["code","chat"],        131072),
    ("kimi-k2.6",               ["code","chat"],        131072),
    ("kimi-k2.5",               ["code","chat"],        131072),
    ("kimi-for-coding",         ["code"],               131072),
    ("doubao-seed-2.0-pro-260215",  ["chat","reasoning"], 131072),
    ("doubao-seed-2.0-code-260215", ["code"],              131072),
    ("doubao-seed-1-6-thinking-250715", ["chat","reasoning"], 131072),
    ("doubao-seed-1-6-flash-250715",    ["code"],              131072),
    ("minimax-m2.7",            ["chat","code"],        131072),
    ("mimo-v2-flash",           ["chat","code"],        131072),
    ("glm-4-7-251222",          ["chat","code"],        131072),
    ("qwen3-32b-20250429",      ["chat","code"],        131072),
]

for name, caps, ctx in catalogs:
    c.execute("INSERT OR REPLACE INTO model_catalog (name, capabilities, context_window) VALUES (?, ?, ?)",
              (name, json.dumps(caps), ctx))

print(f"model_catalog: {len(catalogs)} 条")

# 2. 更新入口
entries = {
    "deepseek": ["deepseek-v4-flash", "deepseek-v4-pro", "deepseek-v4-pro-260425"],
    "kimi":     ["kimi-k2.6", "kimi-k2.5", "kimi-for-coding"],
    "doubao":   ["doubao-seed-2.0-pro-260215", "doubao-seed-2.0-code-260215", "doubao-seed-1-6-flash-250715", "doubao-seed-1-6-thinking-250715"],
    "minimax":  ["minimax-m2.7"],
    "mimo":     ["mimo-v2-flash"],
}

for name, models in entries.items():
    match = json.dumps({"models": models})
    c.execute("UPDATE virtual_models SET match=? WHERE name=?", (match, name))
    print(f"  {name}: {models}")

# 3. 删除已经无用的旧单模型入口
dead = [
    "minimax-m2.7", "kimi-k2.6",  # 已合并到大入口
]
for name in dead:
    c.execute("DELETE FROM virtual_models WHERE name=?", (name,))
    if c.rowcount:
        print(f"  deleted old: {name}")

conn.commit()

# verify
print("\n=== virtual_models ===")
c.execute("SELECT name, match FROM virtual_models ORDER BY id")
for name, match in c.fetchall():
    print(f"  {name:30s}  {match}")

print(f"\n=== model_catalog ({c.execute('SELECT COUNT(*) FROM model_catalog').fetchone()[0]} rows) ===")
c.execute("SELECT name, capabilities FROM model_catalog ORDER BY name")
for name, caps in c.fetchall():
    print(f"  {name:35s}  {caps}")

conn.close()
