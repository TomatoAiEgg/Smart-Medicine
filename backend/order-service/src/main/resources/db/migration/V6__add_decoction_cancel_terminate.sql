alter table decoction_task
    add column if not exists cancel_operation_id varchar(128),
    add column if not exists terminate_operation_id varchar(128),
    add column if not exists cancelled_at timestamptz,
    add column if not exists terminated_at timestamptz;

create unique index if not exists uk_decoction_task_cancel_operation
    on decoction_task (cancel_operation_id)
    where cancel_operation_id is not null;

create unique index if not exists uk_decoction_task_terminate_operation
    on decoction_task (terminate_operation_id)
    where terminate_operation_id is not null;
