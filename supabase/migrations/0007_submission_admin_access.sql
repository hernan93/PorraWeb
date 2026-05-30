-- Allow participants to insert/read their own submissions
grant insert on table submissions to anon;
grant insert on table match_predictions to anon;
grant insert on table group_position_predictions to anon;
grant insert on table third_place_qualifier_predictions to anon;
grant insert on table knockout_predictions to anon;

-- Admin access: authenticated users in admin_users can read/write admin tables
create policy "Admin read participants" on participants
    for select to authenticated
    using (exists (select 1 from admin_users where user_id = auth.uid()));

create policy "Admin update participants" on participants
    for update to authenticated
    using (exists (select 1 from admin_users where user_id = auth.uid()));

create policy "Admin read submissions" on submissions
    for select to authenticated
    using (exists (select 1 from admin_users where user_id = auth.uid()));

create policy "Admin read match predictions" on match_predictions
    for select to authenticated
    using (exists (select 1 from admin_users where user_id = auth.uid()));

create policy "Admin read group position predictions" on group_position_predictions
    for select to authenticated
    using (exists (select 1 from admin_users where user_id = auth.uid()));

create policy "Admin read third place qualifier predictions" on third_place_qualifier_predictions
    for select to authenticated
    using (exists (select 1 from admin_users where user_id = auth.uid()));

create policy "Admin read knockout predictions" on knockout_predictions
    for select to authenticated
    using (exists (select 1 from admin_users where user_id = auth.uid()));

create policy "Admin read email logs" on email_logs
    for select to authenticated
    using (exists (select 1 from admin_users where user_id = auth.uid()));

create policy "Admin update app settings" on app_settings
    for update to authenticated
    using (exists (select 1 from admin_users where user_id = auth.uid()));

create policy "Admin insert match results" on match_results
    for insert to authenticated
    with check (exists (select 1 from admin_users where user_id = auth.uid()));

create policy "Admin update match results" on match_results
    for update to authenticated
    using (exists (select 1 from admin_users where user_id = auth.uid()));

create policy "Admin read scores" on scores
    for select to authenticated
    using (exists (select 1 from admin_users where user_id = auth.uid()));
