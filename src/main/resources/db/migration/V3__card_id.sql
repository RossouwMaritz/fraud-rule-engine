-- Rows stored before cards were tracked get their customer ID as the card, so each customer keeps one card's history.
-- Resending one of those with its real card ID gets 409, because the stored details differ.
alter table transactions add column card_id varchar(64);
update transactions set card_id = customer_id;
alter table transactions alter column card_id set not null;

create index idx_transactions_card_occurred on transactions (card_id, occurred_at desc);
