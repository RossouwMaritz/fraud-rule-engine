create table transactions (
    transaction_id    varchar(64) primary key,
    customer_id       varchar(64) not null,
    amount_minor      bigint      not null check (amount_minor > 0),
    currency          char(3)     not null,
    merchant_id       varchar(64) not null,
    merchant_category varchar(64) not null,
    channel           varchar(16) not null,
    merchant_country  char(2)     not null,
    occurred_at       timestamptz not null,
    received_at       timestamptz not null default now(),
    total_score       int         not null,
    decision          varchar(16) not null,
    rules_errored     text[]      not null default '{}'
);

create index idx_transactions_customer_occurred on transactions (customer_id, occurred_at desc);
create index idx_transactions_decision_occurred on transactions (decision, occurred_at desc);

create table rule_hits (
    id             bigserial primary key,
    transaction_id varchar(64) not null references transactions on delete cascade,
    rule_id        varchar(64) not null,
    score          int         not null,
    reason         text        not null
);

create index idx_rule_hits_transaction on rule_hits (transaction_id);
create index idx_rule_hits_rule on rule_hits (rule_id);
