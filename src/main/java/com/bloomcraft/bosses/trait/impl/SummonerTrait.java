package com.bloomcraft.bosses.trait.impl;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossKeys;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.TraitCategory;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.SafeSpots;
import com.bloomcraft.core.Text;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public final class SummonerTrait extends BaseTrait {

	private static final Map<EntityType<?>, EntityType<?>> MINIONS = new HashMap<>();

	static {
		MINIONS.put(EntityTypes.ZOMBIE, EntityTypes.ZOMBIE);
		MINIONS.put(EntityTypes.HUSK, EntityTypes.HUSK);
		MINIONS.put(EntityTypes.DROWNED, EntityTypes.DROWNED);
		MINIONS.put(EntityTypes.ZOMBIE_VILLAGER, EntityTypes.ZOMBIE);
		MINIONS.put(EntityTypes.SKELETON, EntityTypes.SKELETON);
		MINIONS.put(EntityTypes.STRAY, EntityTypes.STRAY);
		MINIONS.put(EntityTypes.BOGGED, EntityTypes.BOGGED);
		MINIONS.put(EntityTypes.PARCHED, EntityTypes.PARCHED);
		MINIONS.put(EntityTypes.WITHER_SKELETON, EntityTypes.WITHER_SKELETON);
		MINIONS.put(EntityTypes.SPIDER, EntityTypes.CAVE_SPIDER);
		MINIONS.put(EntityTypes.CAVE_SPIDER, EntityTypes.CAVE_SPIDER);
		MINIONS.put(EntityTypes.SILVERFISH, EntityTypes.SILVERFISH);
		MINIONS.put(EntityTypes.ENDERMITE, EntityTypes.ENDERMITE);
		MINIONS.put(EntityTypes.PIGLIN, EntityTypes.PIGLIN);
		MINIONS.put(EntityTypes.PIGLIN_BRUTE, EntityTypes.PIGLIN);
		MINIONS.put(EntityTypes.ZOMBIFIED_PIGLIN, EntityTypes.ZOMBIFIED_PIGLIN);
		MINIONS.put(EntityTypes.PILLAGER, EntityTypes.PILLAGER);
		MINIONS.put(EntityTypes.VINDICATOR, EntityTypes.PILLAGER);
		MINIONS.put(EntityTypes.EVOKER, EntityTypes.VINDICATOR);
		MINIONS.put(EntityTypes.ILLUSIONER, EntityTypes.PILLAGER);
		MINIONS.put(EntityTypes.RAVAGER, EntityTypes.PILLAGER);
		MINIONS.put(EntityTypes.BLAZE, EntityTypes.BLAZE);
		MINIONS.put(EntityTypes.ELDER_GUARDIAN, EntityTypes.GUARDIAN);
		MINIONS.put(EntityTypes.ZOGLIN, EntityTypes.ZOMBIFIED_PIGLIN);
	}

	private int minions = 2;
	private List<Double> thresholds = List.of(75.0, 50.0, 25.0);

	public SummonerTrait() {
		super("summoner", TraitCategory.SPECIAL, "Summoner", "Summoning");
	}

	@Override
	public void load(Cfg s) {
		minions = Math.max(1, s.getInt("minions", 2));
		List<Double> list = s.getDoubleList("health-thresholds");
		thresholds = list.isEmpty() ? List.of(75.0, 50.0, 25.0) : List.copyOf(list);
	}

	@Override
	public String description() {
		StringJoiner joiner = new StringJoiner("/");
		for (double t : thresholds) {
			joiner.add(Text.num(t) + "%");
		}
		return "Calls " + minions + "+ minions at " + joiner + " health. Minions drop nothing.";
	}

	@Override
	public boolean supports(LivingEntity entity) {
		return MINIONS.containsKey(entity.getType());
	}

	@Override
	public void onTick(Boss boss, int now) {
		check(boss, boss.health());
	}

	@Override
	public void afterDamaged(Boss boss, double finalDamage, DamageContext ctx) {
		check(boss, boss.health());
	}

	private void check(Boss boss, double health) {
		if (health <= 0 || !boss.inCombat()) {
			return;
		}
		double percent = health / boss.maxHealth() * 100.0;
		for (double t : thresholds) {
			String flag = "summon_" + t;
			if (percent <= t && !boss.flag(flag)) {
				boss.setFlag(flag, true);
				summon(boss);
				return; // one wave at a time
			}
		}
	}

	private void summon(Boss boss) {
		LivingEntity e = boss.entity();
		EntityType<?> type = MINIONS.get(e.getType());
		if (type == null) {
			return;
		}
		ServerLevel level = boss.level();
		int count = minions + (boss.power() >= 1.5 ? 1 : 0);
		ServerPlayer target = boss.target();
		List<Entity> spawned = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			Entity minion = type.create(level, EntitySpawnReason.MOB_SUMMONED);
			if (minion == null) {
				continue;
			}
			Vec3 at = spot(level, e.position());
			minion.snapTo(at.x, at.y, at.z, e.getYRot(), 0);
			Data.of(minion).putString(BossKeys.MINION, boss.uuid().toString());
			if (minion instanceof Mob mob) {
				mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.MOB_SUMMONED, null);
				mob.setBaby(false);
				for (EquipmentSlot slot : EquipmentSlot.values()) {
					mob.setDropChance(slot, 0f);
				}
				if (target != null) {
					mob.setTarget(target);
				}
			}
			level.addFreshEntity(minion);
			boss.minions().add(minion.getUUID());
			spawned.add(minion);
		}
		Fx.play(e, "entity.evoker.prepare_summon", 1.0f, 1.0f);
		for (Entity m : spawned) {
			Fx.particle(level, ParticleTypes.SOUL, Fx.center(m), 8, 0.3, 0.03);
		}
	}

	private static Vec3 spot(ServerLevel level, Vec3 around) {
		for (int attempt = 0; attempt < 6; attempt++) {
			Vec3 c = around.add(Rng.between(-2.5, 2.5), 0, Rng.between(-2.5, 2.5));
			BlockPos feet = BlockPos.containing(c);
			if (SafeSpots.open(level, feet) && SafeSpots.open(level, feet.above())) {
				return c;
			}
		}
		return around;
	}
}
