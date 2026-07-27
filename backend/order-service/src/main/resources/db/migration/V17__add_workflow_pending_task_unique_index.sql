create unique index if not exists uk_workflow_task_pending_order_type
    on workflow_task (order_id, task_type)
    where task_status = 'PENDING';
