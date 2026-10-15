package com.bloomcraft.bosses.config;

import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.core.Rng;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Set;

/**
 * A group of mobs that share rank weights. Roll #1 (does it become a boss?) uses the global spawn chance times
 * the multiplier; roll #2 (which rank?) uses the weights.
 */
public record MobCategory(
	String id,
	String displayName,
	String description,
	boolean enabled,
	double spawnChanceMultiplier,
	Map<BossRank, Double> weights,
	@Nullable Map<BossRank, Double> netherWeights,
	Set<EntityType<?>> mobs,
	@Nullable Set<EntitySpawnReason> spawnReasons
) {

	/** {@code nether}: the mob spawned in the Nether (uses nether-weights if this category has them). */
	public BossRank rollRank(boolean nether) {
		BossRank rank = Rng.weighted(nether && netherWeights != null ? netherWeights : weights);
		return rank == null ? BossRank.GRAY : rank;
	}

	/** Percent chance of each rank, for the guide book. */
	public double percent(BossRank rank) {
		double total = 0;
		for (double w : weights.values()) {
			total += Math.max(0, w);
		}
		return total <= 0 ? 0 : 100.0 * Math.max(0, weights.getOrDefault(rank, 0.0)) / total;
	}
}
