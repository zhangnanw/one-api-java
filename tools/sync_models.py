"""
sync_models.py — 从 vendor API 拉模型列表，插入数据库中不存在的实例。
用法: python sync_models.py [--dry-run]
"""
import sqlite3, requests, json, os, sys, time

DB = os.path.expandvars(r'%USERPROFILE%\.one-api\one-api.db')
DRY_RUN = '--dry-run' in sys.argv

conn = sqlite3.connect(DB)
c = conn.cursor()

# 读取活跃 vendor
c.execute("SELECT id, name, base_url, api_key FROM vendors WHERE status=1 AND base_url IS NOT NULL AND api_key IS NOT NULL")
vendors = c.fetchall()

new_total = 0
for vid, vname, base, key in vendors:
    url = base.rstrip('/') + '/models'
    print(f"\n--- {vname} (id={vid}) --- {url}")
    try:
        r = requests.get(url, headers={"Authorization": f"Bearer {key}"}, timeout=15)
        if r.status_code != 200:
            print(f"  HTTP {r.status_code}: {r.text[:200]}")
            continue

        data = r.json()
        models = [m.get("id", str(m)) for m in (data.get("data", data) if isinstance(data, dict) else data)]
        print(f"  返回 {len(models)} 个模型")

        added = 0
        for model_name in models:
            # 检查是否已存在：model_name 或 upstream_model 匹配
            c.execute("""
                SELECT id FROM instances 
                WHERE (model_name=? OR upstream_model=?) AND vendor_id=?
            """, (model_name, model_name, vid))
            if c.fetchone():
                continue

            if DRY_RUN:
                print(f"  [DRY] would add: {model_name}")
            else:
                c.execute("""
                    INSERT OR IGNORE INTO instances (vendor_id, model_name, status, upstream_model, meta)
                    VALUES (?, ?, 1, ?, '{}')
                """, (vid, model_name, model_name))
            added += 1

        if added:
            print(f"  + {added} 个新实例")
            new_total += added
        else:
            print(f"  无新实例")

        time.sleep(0.5)  # 别太快
    except Exception as e:
        print(f"  ERR: {e}")

if DRY_RUN:
    print(f"\n[DRY RUN] 共 {new_total} 个新实例待添加")
else:
    conn.commit()
    print(f"\nDone. 共添加 {new_total} 个新实例")
    print(f"总实例数: {c.execute('SELECT COUNT(*) FROM instances').fetchone()[0]}")

conn.close()
