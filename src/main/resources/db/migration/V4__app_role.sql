-- The role the app connects as. It can read and add rows, but can't change or delete them (a stored decision
-- stays as it was made) or change the schema. Flyway runs as the owner of the tables. Compose creates the role
-- with a login first (postgres/init-app-role.sh); anywhere else, create it before migrating, or it's made here
-- without a login. A new table needs its own grant in its migration; AppRoleTest fails until it has one.
do $$
begin
    if not exists (select from pg_roles where rolname = 'fraud_app') then
        create role fraud_app nologin;
    end if;
end
$$;

grant usage on schema public to fraud_app;
grant select, insert on transactions, rule_hits, rule_configs to fraud_app;
grant usage on sequence rule_hits_id_seq to fraud_app; -- rule_hits.id is a bigserial
