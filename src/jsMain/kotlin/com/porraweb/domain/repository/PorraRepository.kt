package com.porraweb.domain.repository

import com.porraweb.domain.model.AdminSettings
import com.porraweb.domain.model.DashboardSummary
import com.porraweb.domain.model.GroupMatch
import com.porraweb.domain.model.KnockoutMatch
import com.porraweb.domain.model.MatchResult
import com.porraweb.domain.model.PaymentParticipant
import com.porraweb.domain.model.RankingEntry
import com.porraweb.domain.model.Rule
import com.porraweb.domain.model.ScoringRule
import com.porraweb.domain.model.Team
import com.porraweb.domain.model.TournamentGroup

interface PorraRepository {
    fun participantRules(): List<Rule>
    fun scoringRules(): List<ScoringRule>
    fun teams(): List<Team>
    fun groups(): List<TournamentGroup>
    fun groupMatches(): List<GroupMatch>
    fun knockoutMatches(): List<KnockoutMatch>
    fun dashboardSummary(): DashboardSummary
    fun ranking(): List<RankingEntry>
    fun latestResults(): List<MatchResult>
    fun pendingParticipants(): List<PaymentParticipant>
    fun adminSettings(): AdminSettings
    fun refresh() {}
}
