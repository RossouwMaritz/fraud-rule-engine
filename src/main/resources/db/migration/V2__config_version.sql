-- Each rule config the app has run with, keyed by the first 12 hex characters of its JSON's SHA-256.
create table rule_configs (
    version       varchar(12) primary key,
    config        jsonb       not null,
    first_seen_at timestamptz not null default now()
);

-- Decisions stored before configs were recorded. Their config is not known, so it stays an empty object.
insert into rule_configs (version, config) values ('unknown', '{}');

alter table transactions add column config_version varchar(12) not null default 'unknown' references rule_configs;
-- The default only fills the existing rows. Every new row must name its own config.
alter table transactions alter column config_version drop default;
