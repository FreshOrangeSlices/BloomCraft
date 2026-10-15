package com.bloomcraft.bosses.feature;

import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.config.MobCategory;
import com.bloomcraft.core.Rng;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Picks a fitting mob for summoned bosses (Totems, Escalation): Overworld mobs in the Overworld, and so on. */
public final class BossMobs {

	private static final List<EntityType<?>> OVERWORLD = List.of(EntityTypes.ZOMBIE, EntityTypes.SKELETON, EntityTypes.HUSK,
		EntityTypes.STRAY, EntityTypes.SPIDER, EntityTypes.CREEPER, EntityTypes.VINDICATOR, EntityTypes.PILLAGER,
		EntityTypes.WITCH, EntityTypes.BOGGED, EntityTypes.PARCHED);
	private static final List<EntityType<?>> NETHER = List.of(EntityTypes.WITHER_SKELETON, EntityTypes.BLAZE,
		EntityTypes.PIGLIN_BRUTE, EntityTypes.HOGLIN, EntityTypes.MAGMA_CUBE, EntityTypes.ZOMBIFIED_PIGLIN);
	private static final List<EntityType<?>> END = List.of(EntityTypes.ENDERMAN);

	private BossMobs() {
	}

	public static EntityType<?> pick(ServerLevel level) {
		List<EntityType<?>> pool = level.dimension() == Level.NETHER ? NETHER : level.dimension() == Level.END ? END : OVERWORLD;
		List<EntityType<?>> allowed = new ArrayList<>();
		for (EntityType<?> type : pool) {
			if (Bosses.get().settings().excludedMobs.contains(type)) {
				continue;
			}
			MobCategory category = Bosses.get().settings().categoryFor(type);
			if (category != null && !category.enabled()) {
				continue;
			}
			allowed.add(type);
		}
		if (allowed.isEmpty()) {
			allowed.addAll(pool);
		}
		return Rng.pick(allowed);
	}

	public static String categoryId(EntityType<?> type) {
		MobCategory category = Bosses.get().settings().categoryFor(type);
		return category != null ? category.id() : "custom";
	}

	public static BossRank rollRank(Map<BossRank, Double> weights, BossRank fallback) {
		BossRank rank = Rng.weighted(weights);
		return rank == null ? fallback : rank;
	}
}
