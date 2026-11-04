#!/usr/bin/env bash
# FarmVerse — database per service, mỗi service hai vai trò (M4, ADR-0005):
#   <svc>_owner  sở hữu database + schema public; Flyway chạy bằng vai trò này (spring.flyway.user)
#   <svc>_app    chỉ SELECT/INSERT/UPDATE/DELETE + dùng sequence; ứng dụng chạy bằng vai trò này
# PUBLIC bị thu hồi CONNECT trên database của service: vai trò của service này không vào được database của service kia.
#
# Chạy được nhiều lần (idempotent):
#   - container postgres mới: docker-entrypoint tự chạy file này (mount vào /docker-entrypoint-initdb.d/)
#   - volume dev đã có từ trước:  docker compose exec -T postgres bash < infra/postgres-init.sh
# Lần chạy lại đồng bộ mật khẩu và chuyển quyền sở hữu bảng cũ (do postgres tạo trước M4) sang <svc>_owner.
#
# Mật khẩu đọc từ biến môi trường (cùng tên service dùng); giá trị mặc định CHỈ cho máy dev.
set -euo pipefail

PSQL=(psql -v ON_ERROR_STOP=1 --no-psqlrc --quiet --username "${POSTGRES_USER:-postgres}")

setup_service() {
  local db="$1" owner="$2" owner_pw="$3" app="$4" app_pw="$5"

  # Vai trò + database (ở database "postgres")
  "${PSQL[@]}" --dbname postgres \
      -v db="$db" -v owner="$owner" -v owner_pw="$owner_pw" -v app="$app" -v app_pw="$app_pw" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE', r)
  FROM unnest(ARRAY[:'owner', :'app']) AS r
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) \gexec
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'owner', :'owner_pw') \gexec
SELECT format('ALTER ROLE %I WITH LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD %L', :'app', :'app_pw') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'owner')
 WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db') \gexec
SELECT format('ALTER DATABASE %I OWNER TO %I', :'db', :'owner') \gexec
SELECT format('REVOKE ALL ON DATABASE %I FROM PUBLIC', :'db') \gexec
SELECT format('GRANT CONNECT ON DATABASE %I TO %I, %I', :'db', :'owner', :'app') \gexec
SQL

  # Schema + quyền trên bảng (ở chính database của service)
  "${PSQL[@]}" --dbname "$db" -v owner="$owner" -v app="$app" <<'SQL'
SELECT format('ALTER SCHEMA public OWNER TO %I', :'owner') \gexec
REVOKE ALL ON SCHEMA public FROM PUBLIC;
SELECT format('GRANT USAGE ON SCHEMA public TO %I', :'app') \gexec
-- Bảng / sequence có sẵn (volume dev tạo trước M4, chủ là postgres) chuyển cho owner
SELECT format('ALTER TABLE public.%I OWNER TO %I', tablename, :'owner')
  FROM pg_tables WHERE schemaname = 'public' AND tableowner <> :'owner' \gexec
SELECT format('ALTER SEQUENCE public.%I OWNER TO %I', sequencename, :'owner')
  FROM pg_sequences WHERE schemaname = 'public' AND sequenceowner <> :'owner' \gexec
SELECT format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', :'app') \gexec
SELECT format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO %I', :'app') \gexec
-- Bảng Flyway tạo về sau (bằng owner) tự cấp quyền DML cho app
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO %I', :'owner', :'app') \gexec
SELECT format('ALTER DEFAULT PRIVILEGES FOR ROLE %I IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO %I', :'owner', :'app') \gexec
SQL
}

setup_service farmverse_identity \
  "${IDENTITY_DB_MIGRATION_USER:-identity_owner}" "${IDENTITY_DB_MIGRATION_PASSWORD:-identity-owner-dev}" \
  "${IDENTITY_DB_USER:-identity_app}" "${IDENTITY_DB_PASSWORD:-identity-app-dev}"

setup_service farmverse_farm \
  "${FARM_DB_MIGRATION_USER:-farm_owner}" "${FARM_DB_MIGRATION_PASSWORD:-farm-owner-dev}" \
  "${FARM_DB_USER:-farm_app}" "${FARM_DB_PASSWORD:-farm-app-dev}"

echo "farmverse: database và vai trò đã sẵn sàng"
