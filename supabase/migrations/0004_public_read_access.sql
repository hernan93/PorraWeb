alter table participants enable row level security;
alter table teams enable row level security;
alter table tournament_groups enable row level security;
alter table group_teams enable row level security;
alter table matches enable row level security;
alter table match_results enable row level security;
alter table submissions enable row level security;
alter table match_predictions enable row level security;
alter table group_position_predictions enable row level security;
alter table third_place_qualifier_predictions enable row level security;
alter table knockout_predictions enable row level security;
alter table scores enable row level security;
alter table email_logs enable row level security;
alter table app_settings enable row level security;
alter table admin_users enable row level security;
alter table venues enable row level security;
alter table fifa_raw_payloads enable row level security;
alter table fifa_sync_logs enable row level security;
alter table score_events enable row level security;

revoke all on table participants from anon, authenticated;
revoke all on table submissions from anon, authenticated;
revoke all on table match_predictions from anon, authenticated;
revoke all on table group_position_predictions from anon, authenticated;
revoke all on table third_place_qualifier_predictions from anon, authenticated;
revoke all on table knockout_predictions from anon, authenticated;
revoke all on table scores from anon, authenticated;
revoke all on table email_logs from anon, authenticated;
revoke all on table admin_users from anon, authenticated;
revoke all on table fifa_raw_payloads from anon, authenticated;
revoke all on table fifa_sync_logs from anon, authenticated;
revoke all on table score_events from anon, authenticated;

grant select on table teams to anon, authenticated;
grant select on table tournament_groups to anon, authenticated;
grant select on table group_teams to anon, authenticated;
grant select on table matches to anon, authenticated;
grant select on table match_results to anon, authenticated;
grant select on table venues to anon, authenticated;
grant select on table app_settings to anon, authenticated;

create policy "Public read teams" on teams
    for select to anon, authenticated using (true);

create policy "Public read tournament groups" on tournament_groups
    for select to anon, authenticated using (true);

create policy "Public read group teams" on group_teams
    for select to anon, authenticated using (true);

create policy "Public read matches" on matches
    for select to anon, authenticated using (true);

create policy "Public read match results" on match_results
    for select to anon, authenticated using (true);

create policy "Public read venues" on venues
    for select to anon, authenticated using (true);

create policy "Public read app settings" on app_settings
    for select to anon, authenticated using (true);

create or replace view public_dashboard_summary as
select
    (select count(*)::integer from participants where approval_status = 'approved') as approved_participants,
    (select count(*)::integer from match_results) as updated_matches,
    case
        when exists (
            select 1 from matches m
            where m.phase = 'final' and m.status = 'finished'
        ) then 'Finalizada'
        when exists (
            select 1 from matches m
            where m.phase <> 'group' and m.kickoff_at <= now()
        ) then 'Eliminatorias 2026'
        else 'Fase de grupos 2026'
    end as current_phase;

create or replace view public_ranking as
with participant_scores as (
    select
        p.id,
        p.name,
        coalesce(group_scores.points, 0) as group_points,
        coalesce(knockout_scores.points, 0) as knockout_points,
        coalesce(total_scores.points, 0) as total_points
    from participants p
    left join scores group_scores on group_scores.participant_id = p.id and group_scores.phase = 'groups'
    left join scores knockout_scores on knockout_scores.participant_id = p.id and knockout_scores.phase = 'knockouts'
    left join scores total_scores on total_scores.participant_id = p.id and total_scores.phase = 'total'
    where p.approval_status = 'approved'
)
select
    row_number() over (order by total_points desc, name asc)::integer as position,
    name as participant_name,
    group_points,
    knockout_points,
    total_points
from participant_scores;

create or replace view public_latest_results as
select
    m.match_number,
    coalesce(home_team.name, m.home_slot, 'Por definir') as home_team,
    case
        when mr.match_id is null then '-'
        else concat(mr.home_goals, ' - ', mr.away_goals)
    end as score,
    coalesce(away_team.name, m.away_slot, 'Por definir') as away_team,
    case
        when m.status = 'finished' then 'Finalizado'
        when m.status = 'in_progress' then 'En juego'
        else 'Pendiente'
    end as status
from matches m
left join teams home_team on home_team.id = m.home_team_id
left join teams away_team on away_team.id = m.away_team_id
left join match_results mr on mr.match_id = m.id
where mr.match_id is not null
order by m.match_number desc;

grant select on public_dashboard_summary to anon, authenticated;
grant select on public_ranking to anon, authenticated;
grant select on public_latest_results to anon, authenticated;
