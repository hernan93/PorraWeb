package com.porraweb.domain.model

data class Rule(
    val number: String,
    val description: String,
)

data class ScoringRule(
    val title: String,
    val items: List<String>,
)

data class Team(
    val id: String,
    val name: String,
)

data class TournamentGroup(
    val id: String = "",
    val code: String,
    val teams: List<Team>,
    val matches: List<GroupMatch>,
)

data class GroupMatch(
    val id: String,
    val groupCode: String,
    val label: String,
    val homeTeam: Team,
    val awayTeam: Team,
)

data class KnockoutMatch(
    val id: String,
    val phase: String,
    val label: String,
    val homeSlot: String,
    val awaySlot: String,
    val options: List<Team>,
)

data class RankingEntry(
    val position: Int,
    val participantName: String,
    val groupPoints: Int,
    val knockoutPoints: Int,
) {
    val totalPoints: Int = groupPoints + knockoutPoints
}

data class DashboardSummary(
    val approvedParticipants: Int,
    val updatedMatches: Int,
    val currentPhase: String,
    val participationPriceEur: String = "5",
    val prizePotEur: String = "0",
)

data class MatchResult(
    val homeTeam: String,
    val score: String,
    val awayTeam: String,
    val status: String,
)

data class PaymentParticipant(
    val participantId: String,
    val name: String,
    val email: String,
    val paymentStatus: String,
    val approvalStatus: String,
)

data class AdminSettings(
    val groupsStatus: String,
    val knockoutsStatus: String,
    val groupDeadline: String,
    val bizumPhone: String,
    val participationPriceEur: String = "5",
)
