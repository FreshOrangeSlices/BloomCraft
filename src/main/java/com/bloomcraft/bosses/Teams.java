package com.bloomcraft.bosses;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.Team;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/** Glow colours come from scoreboard teams: one team per rank. */
public final class Teams {

	private static final String PREFIX = "bc_glow_";

	private Teams() {
	}

	/** Puts the entity in its rank's glow team, or takes it out (rank null). */
	public static void setGlowColor(LivingEntity e, @Nullable BossRank rank) {
		if (!(e.level() instanceof ServerLevel level)) {
			return;
		}
		Scoreboard board = level.getServer().getScoreboard();
		String entry = e.getScoreboardName();
		if (rank == null) {
			PlayerTeam current = board.getPlayersTeam(entry);
			if (current != null && current.getName().startsWith(PREFIX)) {
				board.removePlayerFromTeam(entry, current);
			}
			return;
		}
		String name = PREFIX + rank.id();
		PlayerTeam team = board.getPlayerTeam(name);
		if (team == null) {
			team = board.addPlayerTeam(name);
			team.setColor(Optional.of(rank.teamColor()));
			team.setCollisionRule(Team.CollisionRule.ALWAYS);
		}
		if (board.getPlayersTeam(entry) != team) {
			board.addPlayerToTeam(entry, team);
		}
	}
}
