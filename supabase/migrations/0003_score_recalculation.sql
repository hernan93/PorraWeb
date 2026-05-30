create or replace function recalculate_scores(p_source text default 'manual_recalc')
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_all_groups_complete boolean := false;
    v_score_rows integer := 0;
    v_event_rows integer := 0;
begin
    if p_source not in ('fifa_sync', 'manual_recalc') then
        raise exception 'Invalid score recalculation source: %', p_source;
    end if;

    create temporary table tmp_score_lines (
        participant_id uuid not null,
        phase text not null check (phase in ('groups', 'knockouts')),
        match_id uuid,
        points integer not null check (points > 0),
        reason text not null
    ) on commit drop;

    insert into tmp_score_lines (participant_id, phase, match_id, points, reason)
    select participant_id, 'groups', match_id, points, reason
    from (
        select
            s.participant_id,
            m.id as match_id,
            case
                when mp.home_goals = mr.home_goals and mp.away_goals = mr.away_goals then 3
                when
                    case
                        when mp.home_goals > mp.away_goals then 'home'
                        when mp.home_goals < mp.away_goals then 'away'
                        else 'draw'
                    end =
                    case
                        when mr.home_goals > mr.away_goals then 'home'
                        when mr.home_goals < mr.away_goals then 'away'
                        else 'draw'
                    end then 1
                else 0
            end as points,
            case
                when mp.home_goals = mr.home_goals and mp.away_goals = mr.away_goals then 'group_match_exact_score'
                else 'group_match_result'
            end as reason
        from match_predictions mp
        join submissions s on s.id = mp.submission_id
        join participants p on p.id = s.participant_id
        join matches m on m.id = mp.match_id
        join match_results mr on mr.match_id = m.id
        where s.phase = 'groups'
            and s.status in ('submitted', 'locked')
            and p.approval_status = 'approved'
            and m.phase = 'group'
    ) scored
    where points > 0;

    insert into tmp_score_lines (participant_id, phase, match_id, points, reason)
    select participant_id, 'knockouts', match_id, points, reason
    from (
        select
            s.participant_id,
            m.id as match_id,
            case
                when mp.home_goals = mr.home_goals
                    and mp.away_goals = mr.away_goals
                    and (
                        mr.home_goals <> mr.away_goals
                        or mr.winner_team_id is null
                        or mp.predicted_winner_team_id = mr.winner_team_id
                    ) then 5
                when mr.winner_team_id is not null
                    and mp.predicted_winner_team_id = mr.winner_team_id then 3
                when mr.winner_team_id is not null
                    and mp.predicted_winner_team_id is null
                    and mr.home_goals <> mr.away_goals
                    and case
                        when mp.home_goals > mp.away_goals then 'home'
                        when mp.home_goals < mp.away_goals then 'away'
                        else 'draw'
                    end =
                    case
                        when mr.home_goals > mr.away_goals then 'home'
                        when mr.home_goals < mr.away_goals then 'away'
                        else 'draw'
                    end then 3
                when mr.winner_team_id is null
                    and case
                        when mp.home_goals > mp.away_goals then 'home'
                        when mp.home_goals < mp.away_goals then 'away'
                        else 'draw'
                    end =
                    case
                        when mr.home_goals > mr.away_goals then 'home'
                        when mr.home_goals < mr.away_goals then 'away'
                        else 'draw'
                    end then 3
                else 0
            end as points,
            case
                when mp.home_goals = mr.home_goals and mp.away_goals = mr.away_goals then 'knockout_match_exact_score'
                else 'knockout_match_winner'
            end as reason
        from match_predictions mp
        join submissions s on s.id = mp.submission_id
        join participants p on p.id = s.participant_id
        join matches m on m.id = mp.match_id
        join match_results mr on mr.match_id = m.id
        where s.phase = 'knockouts'
            and s.status in ('submitted', 'locked')
            and p.approval_status = 'approved'
            and m.phase <> 'group'
    ) scored
    where points > 0;

    create temporary table tmp_complete_groups on commit drop as
    select m.group_id
    from matches m
    left join match_results mr on mr.match_id = m.id
    where m.phase = 'group'
        and m.group_id is not null
    group by m.group_id
    having count(*) = 6 and count(mr.match_id) = 6;

    select count(*) = 12 into v_all_groups_complete from tmp_complete_groups;

    create temporary table tmp_group_standings on commit drop as
    with team_results as (
        select
            m.group_id,
            m.home_team_id as team_id,
            case
                when mr.home_goals > mr.away_goals then 3
                when mr.home_goals = mr.away_goals then 1
                else 0
            end as points,
            mr.home_goals as goals_for,
            mr.away_goals as goals_against
        from matches m
        join tmp_complete_groups cg on cg.group_id = m.group_id
        join match_results mr on mr.match_id = m.id
        where m.home_team_id is not null

        union all

        select
            m.group_id,
            m.away_team_id as team_id,
            case
                when mr.away_goals > mr.home_goals then 3
                when mr.away_goals = mr.home_goals then 1
                else 0
            end as points,
            mr.away_goals as goals_for,
            mr.home_goals as goals_against
        from matches m
        join tmp_complete_groups cg on cg.group_id = m.group_id
        join match_results mr on mr.match_id = m.id
        where m.away_team_id is not null
    ), totals as (
        select
            tr.group_id,
            tr.team_id,
            sum(tr.points) as points,
            sum(tr.goals_for) as goals_for,
            sum(tr.goals_against) as goals_against,
            sum(tr.goals_for - tr.goals_against) as goal_difference
        from team_results tr
        group by tr.group_id, tr.team_id
    )
    select
        totals.*,
        row_number() over (
            partition by totals.group_id
            order by totals.points desc, totals.goal_difference desc, totals.goals_for desc, teams.name asc
        ) as position
    from totals
    join teams on teams.id = totals.team_id;

    insert into tmp_score_lines (participant_id, phase, match_id, points, reason)
    select s.participant_id, 'groups', null, 2, 'group_position_exact'
    from group_position_predictions gpp
    join submissions s on s.id = gpp.submission_id
    join participants p on p.id = s.participant_id
    join tmp_group_standings gs on gs.group_id = gpp.group_id and gs.team_id = gpp.team_id
    where s.phase = 'groups'
        and s.status in ('submitted', 'locked')
        and p.approval_status = 'approved'
        and gpp.predicted_position = gs.position;

    insert into tmp_score_lines (participant_id, phase, match_id, points, reason)
    select participant_id, 'groups', null, 2, 'group_perfect_order'
    from (
        select
            s.participant_id,
            gpp.submission_id,
            gpp.group_id,
            count(*) as predicted_rows,
            count(*) filter (where gpp.predicted_position = gs.position) as correct_rows
        from group_position_predictions gpp
        join submissions s on s.id = gpp.submission_id
        join participants p on p.id = s.participant_id
        join tmp_group_standings gs on gs.group_id = gpp.group_id and gs.team_id = gpp.team_id
        where s.phase = 'groups'
            and s.status in ('submitted', 'locked')
            and p.approval_status = 'approved'
        group by s.participant_id, gpp.submission_id, gpp.group_id
    ) grouped
    where predicted_rows = 4 and correct_rows = 4;

    create temporary table tmp_qualified_teams (
        group_id uuid not null,
        team_id uuid not null
    ) on commit drop;

    if v_all_groups_complete then
        insert into tmp_qualified_teams (group_id, team_id)
        select group_id, team_id
        from tmp_group_standings
        where position <= 2;

        insert into tmp_qualified_teams (group_id, team_id)
        select group_id, team_id
        from tmp_group_standings
        where position = 3
        order by points desc, goal_difference desc, goals_for desc, group_id asc
        limit 8;
    end if;

    insert into tmp_score_lines (participant_id, phase, match_id, points, reason)
    select s.participant_id, 'groups', null, 1, 'round_32_qualified_team'
    from group_position_predictions gpp
    join submissions s on s.id = gpp.submission_id
    join participants p on p.id = s.participant_id
    join tmp_qualified_teams qt on qt.group_id = gpp.group_id and qt.team_id = gpp.team_id
    where s.phase = 'groups'
        and s.status in ('submitted', 'locked')
        and p.approval_status = 'approved'
        and gpp.predicted_position in (1, 2);

    insert into tmp_score_lines (participant_id, phase, match_id, points, reason)
    select s.participant_id, 'groups', null, 1, 'round_32_qualified_third_place_team'
    from group_position_predictions gpp
    join submissions s on s.id = gpp.submission_id
    join participants p on p.id = s.participant_id
    join third_place_qualifier_predictions tpq on tpq.submission_id = s.id and tpq.group_id = gpp.group_id
    join tmp_qualified_teams qt on qt.group_id = gpp.group_id and qt.team_id = gpp.team_id
    where s.phase = 'groups'
        and s.status in ('submitted', 'locked')
        and p.approval_status = 'approved'
        and gpp.predicted_position = 3;

    create temporary table tmp_knockout_phase_teams on commit drop as
    select distinct phase, home_team_id as team_id
    from matches
    where phase <> 'group' and home_team_id is not null

    union

    select distinct phase, away_team_id as team_id
    from matches
    where phase <> 'group' and away_team_id is not null;

    insert into tmp_score_lines (participant_id, phase, match_id, points, reason)
    select
        s.participant_id,
        'knockouts',
        null,
        case kp.phase
            when 'round_16' then 4
            when 'quarter_final' then 5
            when 'semi_final' then 6
            when 'third_place' then 7
            when 'final' then 7
        end,
        'knockout_phase_qualified_team'
    from knockout_predictions kp
    join submissions s on s.id = kp.submission_id
    join participants p on p.id = s.participant_id
    join tmp_knockout_phase_teams kt on kt.phase = kp.phase and kt.team_id = kp.predicted_team_id
    where s.phase = 'knockouts'
        and s.status in ('submitted', 'locked')
        and p.approval_status = 'approved'
        and kp.phase in ('round_16', 'quarter_final', 'semi_final', 'third_place', 'final');

    insert into tmp_score_lines (participant_id, phase, match_id, points, reason)
    select s.participant_id, 'knockouts', m.id, 20, 'champion'
    from match_predictions mp
    join submissions s on s.id = mp.submission_id
    join participants p on p.id = s.participant_id
    join matches m on m.id = mp.match_id
    join match_results mr on mr.match_id = m.id
    where s.phase = 'knockouts'
        and s.status in ('submitted', 'locked')
        and p.approval_status = 'approved'
        and m.phase = 'final'
        and mr.winner_team_id is not null
        and mp.predicted_winner_team_id = mr.winner_team_id;

    insert into tmp_score_lines (participant_id, phase, match_id, points, reason)
    select s.participant_id, 'knockouts', m.id, 10, 'runner_up'
    from match_predictions mp
    join submissions s on s.id = mp.submission_id
    join participants p on p.id = s.participant_id
    join matches m on m.id = mp.match_id
    join match_results mr on mr.match_id = m.id
    where s.phase = 'knockouts'
        and s.status in ('submitted', 'locked')
        and p.approval_status = 'approved'
        and m.phase = 'final'
        and mr.winner_team_id in (m.home_team_id, m.away_team_id)
        and mp.predicted_winner_team_id in (m.home_team_id, m.away_team_id)
        and case when mp.predicted_winner_team_id = m.home_team_id then m.away_team_id else m.home_team_id end =
            case when mr.winner_team_id = m.home_team_id then m.away_team_id else m.home_team_id end;

    insert into tmp_score_lines (participant_id, phase, match_id, points, reason)
    select s.participant_id, 'knockouts', m.id, 9, 'third_place'
    from match_predictions mp
    join submissions s on s.id = mp.submission_id
    join participants p on p.id = s.participant_id
    join matches m on m.id = mp.match_id
    join match_results mr on mr.match_id = m.id
    where s.phase = 'knockouts'
        and s.status in ('submitted', 'locked')
        and p.approval_status = 'approved'
        and m.phase = 'third_place'
        and mr.winner_team_id is not null
        and mp.predicted_winner_team_id = mr.winner_team_id;

    insert into tmp_score_lines (participant_id, phase, match_id, points, reason)
    select s.participant_id, 'knockouts', m.id, 8, 'fourth_place'
    from match_predictions mp
    join submissions s on s.id = mp.submission_id
    join participants p on p.id = s.participant_id
    join matches m on m.id = mp.match_id
    join match_results mr on mr.match_id = m.id
    where s.phase = 'knockouts'
        and s.status in ('submitted', 'locked')
        and p.approval_status = 'approved'
        and m.phase = 'third_place'
        and mr.winner_team_id in (m.home_team_id, m.away_team_id)
        and mp.predicted_winner_team_id in (m.home_team_id, m.away_team_id)
        and case when mp.predicted_winner_team_id = m.home_team_id then m.away_team_id else m.home_team_id end =
            case when mr.winner_team_id = m.home_team_id then m.away_team_id else m.home_team_id end;

    delete from score_events where source in ('fifa_sync', 'manual_recalc');

    insert into score_events (participant_id, match_id, source, points_delta, reason)
    select participant_id, match_id, p_source, points, reason
    from tmp_score_lines;

    delete from scores where phase in ('groups', 'knockouts', 'total');

    insert into scores (participant_id, phase, points, updated_at)
    select
        p.id,
        phases.phase,
        coalesce(sum(sl.points), 0)::integer,
        now()
    from participants p
    cross join (values ('groups'), ('knockouts')) as phases(phase)
    left join tmp_score_lines sl on sl.participant_id = p.id and sl.phase = phases.phase
    where p.approval_status = 'approved'
    group by p.id, phases.phase;

    insert into scores (participant_id, phase, points, updated_at)
    select p.id, 'total', coalesce(sum(sl.points), 0)::integer, now()
    from participants p
    left join tmp_score_lines sl on sl.participant_id = p.id
    where p.approval_status = 'approved'
    group by p.id;

    select count(*) into v_score_rows from scores;
    select count(*) into v_event_rows from score_events;

    insert into app_settings (key, value)
    values ('scores_last_recalculated_at', now()::text)
    on conflict (key) do update set value = excluded.value, updated_at = now();

    return jsonb_build_object(
        'source', p_source,
        'scoresRows', v_score_rows,
        'scoreEvents', v_event_rows,
        'allGroupsComplete', v_all_groups_complete
    );
end;
$$;

insert into app_settings (key, value)
values ('scoring_version', '2026-mvp-v1')
on conflict (key) do update set value = excluded.value, updated_at = now();
