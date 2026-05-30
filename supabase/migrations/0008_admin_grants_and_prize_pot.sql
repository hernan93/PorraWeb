grant select, update on table participants to authenticated;
grant select on table submissions to authenticated;
grant select on table match_predictions to authenticated;
grant select on table group_position_predictions to authenticated;
grant select on table third_place_qualifier_predictions to authenticated;
grant select on table knockout_predictions to authenticated;
grant select on table scores to authenticated;
grant select on table email_logs to authenticated;

create or replace view public_dashboard_summary as
with settings as (
    select coalesce(
        (
            select case
                when value ~ '^\d+(\.\d+)?$' then value::numeric
                else null
            end
            from app_settings
            where key = 'participation_price_eur'
        ),
        5
    ) as participation_price_eur
), counts as (
    select
        (select count(*)::integer from participants where approval_status = 'approved') as approved_participants,
        (select count(*)::integer from match_results) as updated_matches
)
select
    counts.approved_participants,
    counts.updated_matches,
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
    end as current_phase,
    trim(to_char(settings.participation_price_eur, 'FM999999990.##')) as participation_price_eur,
    trim(to_char(counts.approved_participants * settings.participation_price_eur, 'FM999999990.##')) as prize_pot_eur
from counts
cross join settings;

grant select on public_dashboard_summary to anon, authenticated;
