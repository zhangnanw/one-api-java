import sqlite3, json, os

db = os.path.expandvars(r'%USERPROFILE%\.one-api\one-api.db')
conn = sqlite3.connect(db)
c = conn.cursor()

# 1. 清空画像，只留标准名
c.execute("DELETE FROM model_catalog")

catalogs = [
    ("deepseek-v4-flash",       ["code","chat"],        131072),
    ("deepseek-v4-pro",         ["code","chat"],        131072),
    ("kimi-k2.6",               ["code","chat"],        131072),
    ("kimi-k2.5",               ["code","chat"],        131072),
    ("kimi-for-coding",         ["code"],               131072),
    ("minimax-m2.7",            ["chat","code"],        131072),
    ("mimo-v2-flash",           ["chat","code"],        131072),
]

for name, caps, ctx in catalogs:
    c.execute("INSERT OR REPLACE INTO model_catalog (name, capabilities, context_window) VALUES (?, ?, ?)",
              (name, json.dumps(caps), ctx))

print(f"model_catalog: {len(catalogs)} 条")

# 2. 更新入口 — doubao 去掉（无标准模型），其余对齐
entries = {
    "deepseek": ["deepseek-v4-flash", "deepseek-v4-pro"],
    "kimi":     ["kimi-k2.6", "kimi-k2.5", "kimi-for-coding"],
    "minimax":  ["minimax-m2.7"],
    "mimo":     ["mimo-v2-flash"],
}

for name, models in entries.items():
    match = json.dumps({"models": models})
    c.execute("UPDATE virtual_models SET match=? WHERE name=?", (match, name))
    print(f"  {name}: {models}")

# 3. 删 doubao 入口（无标准模型）
c.execute("DELETE FROM virtual_models WHERE name='doubao'")
if c.rowcount:
    print("  deleted: doubao (no standard models)")

conn.commit()

# verify
print("\n=== virtual_models ===")
c.execute("SELECT name, match FROM virtual_models ORDER BY id")
for name, match in c.fetchall():
    print(f"  {name:30s}  {match}")

print(f"\n=== model_catalog ({c.execute('SELECT COUNT(*) FROM model_catalog').fetchone()[0]} rows) ===")
c.execute("SELECT name, capabilities FROM model_catalog ORDER BY name")
for name, caps in c.fetchall():
    print(f"  {name:30s}  {caps}")

conn.close()
