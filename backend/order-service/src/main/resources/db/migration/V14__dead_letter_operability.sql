create index if not exists idx_dead_letter_topic_created
    on dead_letter_record (topic, created_at desc);

create unique index if not exists uk_dead_letter_open_event_consumer
    on dead_letter_record (event_id, coalesce(consumer_group, ''))
    where status = 'OPEN';
