drop view if exists public_latest_results;

create view public_latest_results as
select
    m.match_number,
    coalesce(home_team.name, m.home_slot, 'Por definir') as home_team,
    home_team.country_code as home_country_code,
    home_team.fifa_code as home_fifa_code,
    case
        when mr.match_id is null then '-'
        else concat(mr.home_goals, ' - ', mr.away_goals)
    end as score,
    coalesce(away_team.name, m.away_slot, 'Por definir') as away_team,
    away_team.country_code as away_country_code,
    away_team.fifa_code as away_fifa_code,
    case
        when m.status = 'finished' then 'Finalizado'
        when m.status = 'in_progress' then 'En juego'
        else 'Pendiente'
    end as status,
    m.status as match_status,
    m.kickoff_at
from matches m
left join teams home_team on home_team.id = m.home_team_id
left join teams away_team on away_team.id = m.away_team_id
left join match_results mr on mr.match_id = m.id
where mr.match_id is not null
order by
    case m.status when 'in_progress' then 0 when 'finished' then 1 else 2 end,
    m.kickoff_at desc;

grant select on public_latest_results to anon, authenticated;
