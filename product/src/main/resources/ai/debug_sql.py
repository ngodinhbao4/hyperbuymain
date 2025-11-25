import pymysql
import pandas as pd

MYSQL_HOST = "localhost"
MYSQL_PORT = 3307
MYSQL_USER = "root"
MYSQL_PASSWORD = "baotrong"
PRODUCT_DB = "hyperbuy_product_db"

def get_connection(db_name):
    return pymysql.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=db_name,
        cursorclass=pymysql.cursors.DictCursor
    )

sql = """
SELECT
    username,
    product_id,
    SUM(view_count)   AS view_count,
    SUM(buy_count)    AS buy_count,
    AVG(rating_value) AS rating_value
FROM (

    SELECT
        pvh.username    AS username,
        pvh.product_id  AS product_id,
        1               AS view_count,
        0               AS buy_count,
        NULL            AS rating_value
    FROM hyperbuy_product_db.product_view_history pvh

    UNION ALL

    SELECT
        o.user_id       AS username,
        oi.product_id   AS product_id,
        0               AS view_count,
        oi.quantity     AS buy_count,
        NULL            AS rating_value
    FROM hyperbuy_order_db.orders o
    JOIN hyperbuy_order_db.order_items oi
        ON o.id = oi.order_id
    WHERE o.status IN ('CONFIRMED','DELIVERED')

    UNION ALL

    SELECT
        pr.username     AS username,
        pr.product_id   AS product_id,
        0               AS view_count,
        0               AS buy_count,
        pr.rating_value AS rating_value
    FROM hyperbuy_product_db.product_ratings pr

) AS t
GROUP BY username, product_id
LIMIT 20;
"""

if __name__ == "__main__":
    conn = get_connection(PRODUCT_DB)

    print("========= SQL Python ĐANG CHẠY =========")
    print(sql)
    print("========================================")

    with conn.cursor() as cur:
        cur.execute(sql)
        rows = cur.fetchall()

    conn.close()

    print("\nSố dòng nhận được từ cursor:", len(rows))
    print("\n10 dòng đầu (raw):")
    print(rows[:10])

    df = pd.DataFrame(rows)
    print("\n=== Dtypes trong DataFrame ===")
    print(df.dtypes)
    print("\n=== 10 dòng đầu DataFrame ===")
    print(df.head(10))
