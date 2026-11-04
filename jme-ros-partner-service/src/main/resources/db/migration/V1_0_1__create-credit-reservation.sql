CREATE TABLE credit_reservation
(
    id         uuid PRIMARY KEY,
    order_id   varchar                  NOT NULL,
    partner_id varchar                  NOT NULL,
    amount     integer                  NOT NULL,
    created_at timestamp with time zone NOT NULL
);
