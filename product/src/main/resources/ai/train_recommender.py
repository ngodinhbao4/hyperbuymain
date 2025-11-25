import numpy as np
import pandas as pd
import pymysql
from datetime import datetime

# =============================
# 1. THÔNG TIN MYSQL (theo docker-compose)
# =============================
MYSQL_HOST = "localhost"
MYSQL_PORT = 3307            # vì Docker map 3307:3306
MYSQL_USER = "root"
MYSQL_PASSWORD = "baotrong"

PRODUCT_DB = "hyperbuy_product_db"
ORDER_DB = "hyperbuy_order_db"


def get_connection(db_name):
    return pymysql.connect(
        host=MYSQL_HOST,
        port=MYSQL_PORT,
        user=MYSQL_USER,
        password=MYSQL_PASSWORD,
        database=db_name,
        cursorclass=pymysql.cursors.DictCursor
    )


# =============================
# 2. LẤY DỮ LIỆU TỪ 3 NGUỒN (GỘP BẰNG SQL)
# =============================
def load_data():
    conn = get_connection(PRODUCT_DB)

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
        GROUP BY username, product_id;
    """

    # ❌ bỏ pd.read_sql
    with conn.cursor() as cur:
        cur.execute(sql)
        rows = cur.fetchall()
    conn.close()

    df = pd.DataFrame(rows)
    print("Rows after SQL union/group:", len(df))
    print("Dtypes sau khi tạo DataFrame:")
    print(df.dtypes)
    print("5 dòng đầu:")
    print(df.head())

    if df.empty:
        print("Không có dòng (username, product_id) nào → không train được.")
        return pd.DataFrame(columns=["username", "product_id", "rating"])

    # clean như trước (không ép product_id sang numeric nếu bạn để BIGINT / LONG)
    df = df.dropna(subset=["username", "product_id"])
    df["username"] = df["username"].astype(str).str.strip()
    # nếu product_id là số:
    df["product_id"] = pd.to_numeric(df["product_id"], errors="coerce")
    df = df.dropna(subset=["product_id"])
    df["product_id"] = df["product_id"].astype(int)

    df["view_count"] = pd.to_numeric(df["view_count"], errors="coerce").fillna(0.0)
    df["buy_count"] = pd.to_numeric(df["buy_count"], errors="coerce").fillna(0.0)
    df["rating_value"] = pd.to_numeric(df["rating_value"], errors="coerce").fillna(3.0)
    df["rating_value"] = df["rating_value"].clip(1.0, 5.0)

    distinct_pairs = len(df[["username", "product_id"]].drop_duplicates())
    print("Distinct (username, product_id) sau SQL + clean:", distinct_pairs)

    if distinct_pairs == 0:
        print("Sau khi clean vẫn không còn cặp (user, product) nào → dừng.")
        return pd.DataFrame(columns=["username", "product_id", "rating"])

    score = (
        0.3 * np.log1p(df["view_count"]) +
        1.0 * df["buy_count"] +
        0.8 * (df["rating_value"] - 3.0)
    )
    score = score.clip(1.0, 5.0)
    df["rating"] = score

    df_train = df[["username", "product_id", "rating"]].copy()
    print("Total user-product rows:", len(df_train))

    return df_train



# =============================
# 3. TẠO MAPPING USER/ITEM -> INDEX
# =============================
def build_index_mapping(df_train):
    users = df_train["username"].unique()
    products = df_train["product_id"].unique()

    user_to_idx = {u: i for i, u in enumerate(users)}
    idx_to_user = {i: u for u, i in user_to_idx.items()}

    prod_to_idx = {p: i for i, p in enumerate(products)}
    idx_to_prod = {i: p for p, i in prod_to_idx.items()}

    print("Num users:", len(users))
    print("Num products:", len(products))

    return user_to_idx, idx_to_user, prod_to_idx, idx_to_prod


# =============================
# 4. CHUẨN BỊ DỮ LIỆU TRAIN/TEST
# =============================
def build_train_test_tuples(df_train, user_to_idx, prod_to_idx, test_ratio=0.2):
    data = []
    for _, row in df_train.iterrows():
        u = user_to_idx.get(row["username"])
        i = prod_to_idx.get(row["product_id"])
        r = float(row["rating"])
        if u is None or i is None:
            continue
        data.append((u, i, r))

    if len(data) == 0:
        raise ValueError("Không có dữ liệu để train AI (data length = 0)")

    data = np.array(data, dtype=object)
    idx = np.arange(len(data))
    np.random.shuffle(idx)

    if len(data) < 2:
        print("Cảnh báo: dữ liệu quá ít, không thể chia train/test. Sẽ dùng chung cho cả train và test.")
        return data, data

    split = int(len(data) * (1 - test_ratio))
    split = max(1, min(split, len(data) - 1))

    train_idx = idx[:split]
    test_idx = idx[split:]

    train_data = data[train_idx]
    test_data = data[test_idx]

    print("Train size:", len(train_data))
    print("Test size:", len(test_data))

    return train_data, test_data


# =============================
# 5. MATRIX FACTORIZATION (SGD)
# =============================
def train_mf(train_data, num_users, num_items,
             n_factors=20, n_epochs=25, lr=0.01, reg=0.02):
    P = 0.1 * np.random.randn(num_users, n_factors)  # user factors
    Q = 0.1 * np.random.randn(num_items, n_factors)  # item factors

    bu = np.zeros(num_users)
    bi = np.zeros(num_items)

    ratings = np.array([float(r) for (_, _, r) in train_data], dtype=float)
    mu = ratings.mean()
    print(f"Global mean rating: {mu:.4f}")

    for epoch in range(1, n_epochs + 1):
        np.random.shuffle(train_data)
        se = 0.0

        for (u, i, r) in train_data:
            u = int(u)
            i = int(i)
            r = float(r)

            pred = mu + bu[u] + bi[i] + np.dot(P[u], Q[i])
            err = r - pred

            se += err ** 2

            # update biases
            bu[u] += lr * (err - reg * bu[u])
            bi[i] += lr * (err - reg * bi[i])

            # update latent factors
            Pu = P[u].copy()
            Qi = Q[i].copy()
            P[u] += lr * (err * Qi - reg * Pu)
            Q[i] += lr * (err * Pu - reg * Qi)

        rmse_train = np.sqrt(se / len(train_data))
        print(f"Epoch {epoch}/{n_epochs} - Train RMSE: {rmse_train:.4f}")

    return P, Q, bu, bi, mu


def evaluate_mf(test_data, P, Q, bu, bi, mu):
    if test_data is None or len(test_data) == 0:
        print("Không có test_data để evaluate, bỏ qua evaluate.")
        return None

    se = 0.0
    for (u, i, r) in test_data:
        u = int(u)
        i = int(i)
        r = float(r)
        pred = mu + bu[u] + bi[i] + np.dot(P[u], Q[i])
        se += (r - pred) ** 2
    rmse = np.sqrt(se / len(test_data))
    print(f"Test RMSE: {rmse:.4f}")
    return rmse


# =============================
# 6. TẠO GỢI Ý & LƯU DB
# =============================
def save_recommendations(df_train, P, Q, bu, bi, mu,
                         user_to_idx, idx_to_user, idx_to_prod,
                         top_n=20):
    num_users, num_items = P.shape[0], Q.shape[0]

    if num_users == 0 or num_items == 0:
        print("Không có user hoặc product để tạo gợi ý, bỏ qua lưu recommendations.")
        return

    # map product_id -> index
    prod_to_idx = {v: k for k, v in idx_to_prod.items()}
    user_interactions = {u_idx: set() for u_idx in range(num_users)}

    for _, row in df_train.iterrows():
        u = user_to_idx.get(row["username"])
        i = prod_to_idx.get(row["product_id"])
        if u is not None and i is not None:
            user_interactions[u].add(i)

    all_items = np.arange(num_items)
    recommendations = []

    for u_idx in range(num_users):
        interacted = user_interactions.get(u_idx, set())
        if len(interacted) > 0:
            candidates = np.setdiff1d(all_items, np.array(list(interacted), dtype=int))
        else:
            candidates = all_items

        bu_u = bu[u_idx]
        Pu = P[u_idx]
        preds = []
        for i_idx in candidates:
            score = mu + bu_u + bi[i_idx] + np.dot(Pu, Q[i_idx])
            preds.append((i_idx, score))

        preds.sort(key=lambda x: x[1], reverse=True)

        for i_idx, score in preds[:top_n]:
            username = idx_to_user[u_idx]
            product_id = idx_to_prod[i_idx]
            recommendations.append((username, int(product_id), float(score)))

    print("Total recommendations generated:", len(recommendations))

    if not recommendations:
        print("Không có recommendation nào để lưu vào DB.")
        return

    conn_product = get_connection(PRODUCT_DB)
    now = datetime.now().strftime('%Y-%m-%d %H:%M:%S')

    with conn_product.cursor() as cursor:
        cursor.execute("""
            CREATE TABLE IF NOT EXISTS ai_recommendations (
                id BIGINT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(150) NOT NULL,
                product_id BIGINT NOT NULL,
                predicted_score FLOAT NOT NULL,
                created_at DATETIME NOT NULL,
                INDEX idx_user (username),
                INDEX idx_product (product_id)
            );
        """)
        cursor.execute("TRUNCATE TABLE ai_recommendations;")

        insert_sql = """
            INSERT INTO ai_recommendations (username, product_id, predicted_score, created_at)
            VALUES (%s, %s, %s, %s)
        """

        cursor.executemany(insert_sql, [
            (u, p, s, now) for (u, p, s) in recommendations
        ])
        conn_product.commit()

    conn_product.close()
    print("Saved recommendations into ai_recommendations.")


# =============================
# 7. MAIN
# =============================
def main():
    print("=== LOAD DATA ===")
    df_train = load_data()

    if df_train is None or df_train.empty:
        print("df_train trống → Không train được mô hình. Thoát script.")
        return

    user_to_idx, idx_to_user, prod_to_idx, idx_to_prod = build_index_mapping(df_train)

    num_users = len(user_to_idx)
    num_items = len(prod_to_idx)

    if num_users == 0 or num_items == 0:
        raise ValueError("Không có user hoặc product nào sau khi build mapping.")

    print("=== BUILD TRAIN/TEST ===")
    train_data, test_data = build_train_test_tuples(df_train, user_to_idx, prod_to_idx)

    print("=== TRAIN MATRIX FACTORIZATION ===")
    P, Q, bu, bi, mu = train_mf(
        train_data=train_data,
        num_users=num_users,
        num_items=num_items,
        n_factors=20,
        n_epochs=25,
        lr=0.01,
        reg=0.02
    )

    print("=== EVALUATE ON TEST SET ===")
    evaluate_mf(test_data, P, Q, bu, bi, mu)

    print("=== GENERATE & SAVE RECOMMENDATIONS ===")
    save_recommendations(
        df_train=df_train,
        P=P, Q=Q, bu=bu, bi=bi, mu=mu,
        user_to_idx=user_to_idx,
        idx_to_user=idx_to_user,
        idx_to_prod=idx_to_prod,
        top_n=20
    )

    print("=== DONE ===")


if __name__ == "__main__":
    main()
