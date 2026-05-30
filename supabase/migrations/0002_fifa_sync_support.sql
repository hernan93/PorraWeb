create extension if not exists pgcrypto;

create table if not exists venues (
    id uuid primary key default gen_random_uuid(),
    fifa_stadium_id text unique,
    name text not null,
    city_name text,
    country_code text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table teams add column if not exists fifa_team_id text;
alter table teams add column if not exists short_name text;
alter table teams add column if not exists country_code text;
alter table teams add column if not exists updated_at timestamptz not null default now();

create unique index if not exists teams_fifa_team_id_key on teams (fifa_team_id);
create index if not exists teams_country_code_idx on teams (country_code);

alter table tournament_groups add column if not exists fifa_group_id text;
alter table tournament_groups add column if not exists updated_at timestamptz not null default now();

create unique index if not exists tournament_groups_fifa_group_id_key on tournament_groups (fifa_group_id);

alter table matches add column if not exists fifa_match_id text;
alter table matches add column if not exists fifa_stage_id text;
alter table matches add column if not exists fifa_group_id text;
alter table matches add column if not exists venue_id uuid references venues(id);
alter table matches add column if not exists home_slot text;
alter table matches add column if not exists away_slot text;
alter table matches add column if not exists stage_name text;
alter table matches add column if not exists group_name text;
alter table matches add column if not exists source_url text;
alter table matches add column if not exists source_checked_at timestamptz;
alter table matches add column if not exists fifa_payload_hash text;
alter table matches add column if not exists updated_at timestamptz not null default now();

create unique index if not exists matches_fifa_match_id_key on matches (fifa_match_id);
create index if not exists matches_fifa_stage_id_idx on matches (fifa_stage_id);
create index if not exists matches_fifa_group_id_idx on matches (fifa_group_id);
create index if not exists matches_venue_id_idx on matches (venue_id);
create index if not exists matches_kickoff_at_idx on matches (kickoff_at);

alter table match_results add column if not exists home_penalty_goals integer check (home_penalty_goals is null or home_penalty_goals >= 0);
alter table match_results add column if not exists away_penalty_goals integer check (away_penalty_goals is null or away_penalty_goals >= 0);
alter table match_results add column if not exists result_source text not null default 'manual'
    check (result_source in ('manual', 'fifa'));
alter table match_results add column if not exists is_manual_override boolean not null default false;
alter table match_results add column if not exists source_checked_at timestamptz;
alter table match_results add column if not exists fifa_payload_hash text;

create table if not exists fifa_raw_payloads (
    id uuid primary key default gen_random_uuid(),
    source text not null default 'fifa-api',
    endpoint text not null,
    payload_hash text not null,
    payload jsonb not null,
    fetched_at timestamptz not null default now()
);

create index if not exists fifa_raw_payloads_fetched_at_idx on fifa_raw_payloads (fetched_at desc);
create index if not exists fifa_raw_payloads_payload_hash_idx on fifa_raw_payloads (payload_hash);

create table if not exists fifa_sync_logs (
    id uuid primary key default gen_random_uuid(),
    sync_type text not null default 'matches',
    status text not null check (status in ('running', 'success', 'failed')),
    source_url text,
    started_at timestamptz not null default now(),
    finished_at timestamptz,
    matches_seen integer not null default 0,
    matches_upserted integer not null default 0,
    results_upserted integer not null default 0,
    error_message text
);

create index if not exists fifa_sync_logs_started_at_idx on fifa_sync_logs (started_at desc);
create index if not exists fifa_sync_logs_status_idx on fifa_sync_logs (status);

create table if not exists score_events (
    id uuid primary key default gen_random_uuid(),
    participant_id uuid references participants(id) on delete cascade,
    match_id uuid references matches(id) on delete cascade,
    source text not null check (source in ('fifa_sync', 'manual_recalc')),
    points_delta integer not null,
    reason text not null,
    created_at timestamptz not null default now()
);

create index if not exists score_events_participant_id_idx on score_events (participant_id);
create index if not exists score_events_match_id_idx on score_events (match_id);

insert into app_settings (key, value) values
    ('fifa_api_base_url', 'https://api.fifa.com/api/v3'),
    ('fifa_competition_id', '17'),
    ('fifa_season_id', '285023'),
    ('fifa_api_language', 'en')
on conflict (key) do update set
    value = excluded.value,
    updated_at = now();
