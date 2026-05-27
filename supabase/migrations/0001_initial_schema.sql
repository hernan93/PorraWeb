create extension if not exists pgcrypto;

create table participants (
    id uuid primary key default gen_random_uuid(),
    name text not null,
    email text not null,
    normalized_email text not null unique,
    payment_status text not null default 'pending_payment'
        check (payment_status in ('pending_payment', 'paid_bizum', 'paid_cash', 'rejected')),
    approval_status text not null default 'pending'
        check (approval_status in ('pending', 'approved', 'rejected', 'needs_review')),
    created_at timestamptz not null default now(),
    approved_at timestamptz
);

create table teams (
    id uuid primary key default gen_random_uuid(),
    fifa_code text unique,
    name text not null unique,
    created_at timestamptz not null default now()
);

create table tournament_groups (
    id uuid primary key default gen_random_uuid(),
    code text not null unique,
    name text not null
);

create table group_teams (
    group_id uuid not null references tournament_groups(id) on delete cascade,
    team_id uuid not null references teams(id) on delete cascade,
    primary key (group_id, team_id)
);

create table matches (
    id uuid primary key default gen_random_uuid(),
    match_number integer unique,
    phase text not null check (phase in ('group', 'round_32', 'round_16', 'quarter_final', 'semi_final', 'third_place', 'final')),
    group_id uuid references tournament_groups(id),
    home_team_id uuid references teams(id),
    away_team_id uuid references teams(id),
    kickoff_at timestamptz,
    status text not null default 'scheduled' check (status in ('scheduled', 'in_progress', 'finished')),
    created_at timestamptz not null default now()
);

create table match_results (
    match_id uuid primary key references matches(id) on delete cascade,
    home_goals integer not null check (home_goals >= 0),
    away_goals integer not null check (away_goals >= 0),
    winner_team_id uuid references teams(id),
    updated_at timestamptz not null default now()
);

create table submissions (
    id uuid primary key default gen_random_uuid(),
    participant_id uuid not null references participants(id) on delete cascade,
    phase text not null check (phase in ('groups', 'knockouts')),
    status text not null default 'submitted' check (status in ('submitted', 'locked', 'cancelled')),
    submitted_at timestamptz not null default now(),
    unique (participant_id, phase)
);

create table match_predictions (
    id uuid primary key default gen_random_uuid(),
    submission_id uuid not null references submissions(id) on delete cascade,
    match_id uuid not null references matches(id) on delete cascade,
    home_goals integer not null check (home_goals >= 0),
    away_goals integer not null check (away_goals >= 0),
    predicted_winner_team_id uuid references teams(id),
    unique (submission_id, match_id)
);

create table group_position_predictions (
    id uuid primary key default gen_random_uuid(),
    submission_id uuid not null references submissions(id) on delete cascade,
    group_id uuid not null references tournament_groups(id) on delete cascade,
    team_id uuid not null references teams(id) on delete cascade,
    predicted_position integer not null check (predicted_position between 1 and 4),
    unique (submission_id, group_id, team_id),
    unique (submission_id, group_id, predicted_position)
);

create table third_place_qualifier_predictions (
    id uuid primary key default gen_random_uuid(),
    submission_id uuid not null references submissions(id) on delete cascade,
    group_id uuid not null references tournament_groups(id) on delete cascade,
    unique (submission_id, group_id)
);

create table knockout_predictions (
    id uuid primary key default gen_random_uuid(),
    submission_id uuid not null references submissions(id) on delete cascade,
    phase text not null check (phase in ('round_32', 'round_16', 'quarter_final', 'semi_final', 'third_place', 'final')),
    slot_code text not null,
    predicted_team_id uuid not null references teams(id),
    unique (submission_id, phase, slot_code)
);

create table scores (
    id uuid primary key default gen_random_uuid(),
    participant_id uuid not null references participants(id) on delete cascade,
    phase text not null check (phase in ('groups', 'knockouts', 'total')),
    points integer not null default 0,
    updated_at timestamptz not null default now(),
    unique (participant_id, phase)
);

create table email_logs (
    id uuid primary key default gen_random_uuid(),
    participant_id uuid references participants(id) on delete set null,
    email text not null,
    template text not null,
    status text not null default 'pending' check (status in ('pending', 'sent', 'failed')),
    provider_message_id text,
    error_message text,
    created_at timestamptz not null default now()
);

create table app_settings (
    key text primary key,
    value text not null,
    updated_at timestamptz not null default now()
);

create table admin_users (
    user_id uuid primary key references auth.users(id) on delete cascade,
    email text not null unique,
    created_at timestamptz not null default now()
);

insert into app_settings (key, value) values
    ('groups_form_status', 'open'),
    ('knockouts_form_status', 'closed'),
    ('participation_price_eur', '5')
on conflict (key) do nothing;
