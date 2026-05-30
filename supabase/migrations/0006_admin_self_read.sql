grant select on table admin_users to authenticated;

create policy "Admin users can read own admin row" on admin_users
    for select to authenticated
    using (user_id = auth.uid());
