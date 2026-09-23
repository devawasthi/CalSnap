CREATE TABLE users (
 id uuid PRIMARY KEY, email text NOT NULL, name text NOT NULL,
 oauth_provider text NOT NULL, oauth_subject text NOT NULL,
 timezone text NOT NULL DEFAULT 'UTC', created_at timestamptz NOT NULL DEFAULT now(),
 UNIQUE(oauth_provider, oauth_subject)
);
CREATE TABLE goals (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users ON DELETE CASCADE,
 daily_calories numeric(10,2) NOT NULL CHECK(daily_calories BETWEEN 1 AND 20000),
 daily_protein_g numeric(10,2) NOT NULL CHECK(daily_protein_g BETWEEN 1 AND 1000),
 effective_from date NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX goals_active ON goals(user_id,effective_from DESC,created_at DESC);
CREATE TABLE scans (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users ON DELETE CASCADE,
 image_url text NOT NULL, recognized_items jsonb NOT NULL DEFAULT '[]',
 status text NOT NULL CHECK(status IN ('pending','confirmed','discarded')),
 created_at timestamptz NOT NULL DEFAULT now(), UNIQUE(id,user_id)
);
CREATE TABLE food_logs (
 id uuid PRIMARY KEY, user_id uuid NOT NULL REFERENCES users ON DELETE CASCADE,
 scan_id uuid, item_index int,
 food_name text NOT NULL CHECK(length(food_name) BETWEEN 1 AND 200),
 calories numeric(12,2) NOT NULL CHECK(calories BETWEEN 0 AND 50000),
 protein_g numeric(12,2) NOT NULL CHECK(protein_g BETWEEN 0 AND 10000),
 carbs_g numeric(12,2) NOT NULL CHECK(carbs_g BETWEEN 0 AND 10000),
 fat_g numeric(12,2) NOT NULL CHECK(fat_g BETWEEN 0 AND 10000),
 quantity_multiplier numeric(10,3) NOT NULL CHECK(quantity_multiplier > 0),
 portion_g numeric(10,2) NOT NULL CHECK(portion_g > 0),
 logged_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
 FOREIGN KEY(scan_id,user_id) REFERENCES scans(id,user_id), UNIQUE(scan_id,item_index)
);
CREATE INDEX food_logs_day ON food_logs(user_id,logged_at DESC);
CREATE TABLE nutrition_cache (
 user_id uuid NOT NULL REFERENCES users ON DELETE CASCADE, query text NOT NULL,
 result jsonb NOT NULL, expires_at timestamptz NOT NULL, PRIMARY KEY(user_id,query)
);
CREATE TABLE scan_usage (
 user_id uuid NOT NULL REFERENCES users ON DELETE CASCADE, window_start timestamptz NOT NULL,
 attempts int NOT NULL CHECK(attempts > 0), PRIMARY KEY(user_id,window_start)
);
