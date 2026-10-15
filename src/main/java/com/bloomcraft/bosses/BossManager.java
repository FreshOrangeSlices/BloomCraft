package com.bloomcraft.bosses;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.bosses.config.BossSettings;
import com.bloomcraft.bosses.config.MobCategory;
import com.bloomcraft.bosses.config.MobProfile;
import com.bloomcraft.bosses.config.RankSettings;
import com.bloomcraft.bosses.combat.AreaDamage;
import com.bloomcraft.bosses.item.BossItems;
import com.bloomcraft.bosses.trait.BaseTrait;
import com.bloomcraft.bosses.trait.BossTrait;
import com.bloomcraft.bosses.trait.Synergies;
import com.bloomcraft.core.Attrs;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Motion;
import com.bloomcraft.core.QuestFlags;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.SafeSpots;
import com.bloomcraft.core.Text;
import com.bloomcraft.core.Ticks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Turns mobs into bosses (roll #1: is it a boss? roll #2: which rank?), applies their stats, tracks the loaded ones,
 * runs the shared ticker, and cleans up on death, despawn and unload.
 */
public final class BossManager {

	private final Bosses bosses;
	private final Map<UUID, Boss> active = new HashMap<>();

	public BossManager(Bosses bosses) {
		this.bosses = bosses;
	}

	private BossSettings settings() {
		return bosses.settings();
	}

	// =====================================================================
	// Lookup
	// =====================================================================

	public @Nullable Boss get(@Nullable Entity entity) {
		return entity == null ? null : active.get(entity.getUUID());
	}

	public @Nullable Boss get(UUID id) {
		return active.get(id);
	}

	public Collection<Boss> active() {
		return Collections.unmodifiableCollection(active.values());
	}

	public static boolean isBoss(Entity entity) {
		return Data.flag(entity, BossKeys.BOSS);
	}

	public static boolean isMinion(Entity entity) {
		return Data.has(entity, BossKeys.MINION);
	}

	/** A pet (from the pets part of the mod): never a boss, never hunted or angered by curses. */
	public static boolean isPet(Entity entity) {
		return Data.has(entity, "pet");
	}

	// =====================================================================
	// Natural spawns
	// =====================================================================

	/**
	 * From Mob#finalizeSpawn: a mob just spawned (it isn't in the world yet, and its own spawn code may still be
	 * equipping it). An eligible mob is marked, and gets its boss roll when it's added to the world (see onLoad).
	 * Mobs placed with the world (structures) get theirs the first time they load near a player.
	 */
	public void handleSpawn(Mob entity, EntitySpawnReason reason) {
		BossSettings s = settings();
		if (!s.enabled || isBoss(entity) || isMinion(entity) || isPet(entity) || Data.has(entity, BossKeys.STATUE)) {
			return;
		}
		if (eligible(entity, reason)) {
			Data.of(entity).putString(BossKeys.PENDING_ROLL, reason.name());
		}
	}

	private boolean eligible(LivingEntity entity, EntitySpawnReason reason) {
		BossSettings s = settings();
		MobCategory category = s.categoryFor(entity.getType());
		if (category == null || !category.enabled() || s.excludedMobs.contains(entity.getType())) {
			return false;
		}
		return (category.spawnReasons() != null ? category.spawnReasons() : s.spawnReasons).contains(reason);
	}

	private void roll(Mob entity, ServerLevel level) {
		BossSettings s = settings();
		MobCategory category = s.categoryFor(entity.getType());
		if (category == null || !s.worldAllowed(level) || !nearPlayerHeight(level, entity, s.maxHeightDifference)) {
			return;
		}
		// Roll #1: does this mob become a boss at all?
		if (!Rng.chance(s.spawnChance * category.spawnChanceMultiplier())) {
			return;
		}
		if (regularCount() >= s.maxActive || tooCloseToAnotherBoss(entity)) {
			return;
		}
		// Roll #2: which rank? (the Nether rolls higher)
		BossRank rank = category.rollRank(level.dimension() == Level.NETHER);
		createBoss(entity, rank, category.id(), null, true);
	}

	public int regularCount() {
		int n = 0;
		for (Boss b : active.values()) {
			if (!b.isNemesis()) {
				n++;
			}
		}
		return n;
	}

	/**
	 * Spawns a brand-new boss of this type (totems, escalation, Nemesis returns, admin). Ignores the boss cap.
	 * Returns null if the type isn't a mob or can't exist here.
	 */
	public @Nullable Boss summon(EntityType<?> type, ServerLevel level, Vec3 at, BossRank rank,
								 @Nullable List<BossTrait> traits, boolean announce, String category) {
		if (level.getDifficulty() == Difficulty.PEACEFUL && !type.isAllowedInPeaceful()) {
			return null; // it would vanish on its first tick
		}
		Entity created = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (!(created instanceof Mob mob)) {
			if (created != null) {
				created.discard();
			}
			return null;
		}
		mob.snapTo(at.x, at.y, at.z, Rng.r().nextFloat() * 360f, 0);
		Data.of(mob).putBoolean(BossKeys.BOSS, true); // so finalizeSpawn doesn't roll it again
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(at)), EntitySpawnReason.MOB_SUMMONED, null);
		Boss boss = createBoss(mob, rank, category, traits, announce);
		if (!level.addFreshEntity(mob)) {
			unregister(boss);
			return null;
		}
		return boss;
	}

	/** Marks a player-made boss (totem, escalation, command, challenge): it never drops Souls or Totems. */
	public static void markCreated(Boss boss, String how) {
		Data.of(boss.entity()).putString(BossKeys.CREATED, how);
	}

	public static @Nullable String created(LivingEntity e) {
		return Data.str(e, BossKeys.CREATED);
	}

	/** The rank a promoted boss had before anyone offered it a Soul (its own rank if it was never promoted). */
	public static BossRank wildRank(LivingEntity e, BossRank now) {
		BossRank wild = BossRank.parse(Data.str(e, BossKeys.WILD_RANK));
		return wild != null && !wild.atLeast(now) ? wild : now;
	}

	/** Whether a player (not spectating) is around this mob, within {@code maxDy} blocks above or below it. */
	private static boolean nearPlayerHeight(ServerLevel level, LivingEntity entity, int maxDy) {
		for (ServerPlayer p : level.players()) {
			if (p.isSpectator()) {
				continue;
			}
			double dx = p.getX() - entity.getX();
			double dz = p.getZ() - entity.getZ();
			if (dx * dx + dz * dz <= 128 * 128 && (maxDy <= 0 || Math.abs(p.getY() - entity.getY()) <= maxDy)) {
				return true;
			}
		}
		return false;
	}

	private boolean tooCloseToAnotherBoss(Entity entity) {
		double min = settings().minDistanceBetween;
		if (min <= 0) {
			return false;
		}
		for (Boss boss : active.values()) {
			LivingEntity e = boss.entity();
			if (e.level() == entity.level() && e.distanceToSqr(entity) < min * min) {
				return true;
			}
		}
		return false;
	}

	// =====================================================================
	// Creation / restore
	// =====================================================================

	/** Turns a mob into a boss. {@code traits} may be null to roll them. Works before and after it's in the world. */
	public Boss createBoss(LivingEntity entity, BossRank rank, String categoryId, @Nullable List<BossTrait> traits,
						   boolean announce) {
		return createBoss(entity, rank, categoryId, traits, announce, Set.of());
	}

	/** {@code alreadyApplied}: trait ids the mob already carries (a promotion), so their one-time setup isn't repeated. */
	private Boss createBoss(LivingEntity entity, BossRank rank, String categoryId, @Nullable List<BossTrait> traits,
							boolean announce, Set<String> alreadyApplied) {
		BossSettings s = settings();
		RankSettings rs = s.rank(rank);
		MobProfile profile = s.profileFor(entity.getType());

		if (traits == null) {
			int count = Rng.between(rs.traits().min(), rs.traits().max());
			traits = bosses.traits().roll(entity, count);
		}

		applyStats(entity, rank, rs.stats(), profile);

		CompoundTag data = Data.of(entity);
		data.putBoolean(BossKeys.BOSS, true);
		data.putString(BossKeys.RANK, rank.name());
		data.putString(BossKeys.CATEGORY, categoryId);
		data.remove(BossKeys.PENDING_ROLL);
		List<String> ids = new ArrayList<>();
		for (BossTrait t : traits) {
			ids.add(t.id());
		}
		Data.strings(data, BossKeys.TRAITS, ids);

		Boss boss = build(entity, rank, categoryId, traits);
		entity.setCustomName(boss.name());
		entity.setCustomNameVisible(s.alwaysShowName);
		if (entity instanceof Mob mob) {
			mob.setCanPickUpLoot(false);
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				mob.setDropChance(slot, 0f);
			}
		}
		if (s.armorSets && !Data.has(entity, BossKeys.NEMESIS)) {
			BossArmor.applyRankSet(s, entity, rank);
		}
		for (BossTrait t : traits) {
			t.onApply(boss, !alreadyApplied.contains(t.id()));
		}
		register(boss);
		if (announce) {
			bosses.later(1, () -> {
				if (active.get(boss.uuid()) == boss) {
					bosses.presentation().announceSpawn(boss);
				}
			});
		}
		return boss;
	}

	/** From the entity-load event: rebuilds the boss from its saved data, or rolls a structure mob. */
	public void onLoad(Entity entity) {
		if (!(entity instanceof LivingEntity living) || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		if (isBoss(entity)) {
			restore(living);
			return;
		}
		CompoundTag data = Data.peek(entity);
		if (data == null || !data.contains(BossKeys.PENDING_ROLL) || !(entity instanceof Mob mob)) {
			return;
		}
		String reason = data.getStringOr(BossKeys.PENDING_ROLL, "");
		boolean placedWithWorld = reason.equals("STRUCTURE") || reason.equals("CHUNK_GENERATION");
		if (placedWithWorld && !nearPlayerHeight(level, living, settings().maxHeightDifference)) {
			return; // a structure mob gets its roll the first time it loads near a player
		}
		data.remove(BossKeys.PENDING_ROLL); // each mob rolls once
		roll(mob, level);
	}

	/** Rebuilds the in-memory boss from the entity's saved data. */
	public @Nullable Boss restore(LivingEntity entity) {
		if (active.containsKey(entity.getUUID()) || !isBoss(entity)) {
			return active.get(entity.getUUID());
		}
		CompoundTag data = Data.of(entity);
		BossRank rank = BossRank.parse(data.getStringOr(BossKeys.RANK, "GRAY"));
		if (rank == null) {
			rank = BossRank.GRAY;
		}
		String category = data.getStringOr(BossKeys.CATEGORY, "custom");
		List<BossTrait> traits = new ArrayList<>();
		for (String id : Data.strings(data, BossKeys.TRAITS)) {
			BossTrait t = bosses.traits().get(id.trim());
			if (t != null && bosses.traits().isEnabled(t)) {
				traits.add(t);
			}
		}
		Data.strings(data, BossKeys.TRAITS, traits.stream().map(BossTrait::id).toList());
		String nemesisId = data.getString(BossKeys.NEMESIS).orElse(null);
		if (nemesisId != null && !bosses.nemesis().canReattach(nemesisId, entity)) {
			bosses.later(1, entity::discard); // a stray copy: its record is gone or it's already out
			return null;
		}
		Boss boss = build(entity, rank, category, traits);
		entity.setCustomNameVisible(settings().alwaysShowName);
		entity.setGlowingTag(false); // the ticker turns it back on with its colour
		boss.setUndyingUsed(data.getBooleanOr(BossKeys.UNDYING, false));
		if (data.getBooleanOr(BossKeys.LAST_STAND, false)) {
			boss.setLastStand(true);
			boss.scalePower(1.0 + settings().lastStandPowerBonus / 100.0);
		}
		for (BossTrait t : traits) {
			t.onApply(boss, false);
		}
		register(boss);
		if (nemesisId != null) {
			bosses.nemesis().reattach(boss, nemesisId);
		}
		return boss;
	}

	/** Carries boss status over when a boss converts (Zombie to Drowned, Skeleton to Stray...). */
	public void transfer(Boss from, LivingEntity to) {
		List<BossTrait> keep = new ArrayList<>();
		for (BossTrait t : from.traits()) {
			if (t.supports(to)) {
				keep.add(t);
			}
		}
		double ratio = from.healthRatio();
		CompoundTag old = Data.of(from.entity());
		CompoundTag fresh = Data.of(to);
		for (String key : List.of(BossKeys.CREATED, BossKeys.PLAYER_KILLS, BossKeys.WILD_RANK)) {
			if (old.contains(key)) {
				fresh.put(key, old.get(key).copy());
			}
		}
		Boss boss = createBoss(to, from.rank(), from.categoryId(), keep, false);
		to.setHealth((float) Math.max(1.0, to.getMaxHealth() * ratio));
		if (from.undyingUsed()) {
			boss.setUndyingUsed(true);
			fresh.putBoolean(BossKeys.UNDYING, true);
		}
	}

	private Boss build(LivingEntity entity, BossRank rank, String categoryId, List<BossTrait> traits) {
		RankSettings rs = settings().rank(rank);
		MobProfile profile = settings().profileFor(entity.getType());
		Synergies.Synergy synergy = Synergies.find(traits);
		String adjective = synergy != null ? synergy.title() + " " : traits.isEmpty() ? "" : traits.get(0).adjective() + " ";
		String mobName = entity.getType().getDescription().getString();
		String plain = rank.starText() + " " + rs.name() + " " + adjective + mobName;
		MutableComponent styled = rank.styled(plain);
		double damage = MobProfile.scaleMultiplier(rs.stats().damage(), profile.damage())
			* settings().difficultyDamage
			* (1.0 + threat(entity) * settings().threatMaxDamage / 100.0);
		double power = rs.traits().power() * (synergy != null ? Synergies.POWER_BONUS : 1.0);
		return new Boss(entity, rank, categoryId, traits, styled, plain, damage, power);
	}

	private void applyStats(LivingEntity e, BossRank rank, RankSettings.Stats st, MobProfile p) {
		// Higher ranks notice players from further away.
		Attrs.set(e, Attributes.FOLLOW_RANGE, BossKeys.MOD_FOLLOW, rank.ordinal() * 6.0, AttributeModifier.Operation.ADD_VALUE);
		Attrs.set(e, Attributes.MAX_HEALTH, BossKeys.MOD_HEALTH, MobProfile.scaleMultiplier(st.health(), p.health()) - 1.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Attrs.set(e, Attributes.ARMOR, BossKeys.MOD_ARMOR, st.armor() * p.defense(), AttributeModifier.Operation.ADD_VALUE);
		Attrs.set(e, Attributes.ARMOR_TOUGHNESS, BossKeys.MOD_TOUGHNESS, st.toughness() * p.defense(),
			AttributeModifier.Operation.ADD_VALUE);
		Attrs.set(e, Attributes.KNOCKBACK_RESISTANCE, BossKeys.MOD_KNOCKBACK, st.knockbackResistance() * p.defense(),
			AttributeModifier.Operation.ADD_VALUE);
		Attrs.set(e, Attributes.MOVEMENT_SPEED, BossKeys.MOD_SPEED, MobProfile.scaleMultiplier(st.speed(), p.speed()) - 1.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Attrs.set(e, Attributes.SCALE, BossKeys.MOD_SIZE, st.size() * p.size(), AttributeModifier.Operation.ADD_VALUE);
		Attrs.set(e, Attributes.MAX_HEALTH, BossKeys.MOD_DIFFICULTY, settings().difficultyHealth - 1.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		applyHealthFloor(e, st.minHealth());
		e.setHealth(e.getMaxHealth());
	}

	/** Top ranks have a minimum health (Legendary at least a Warden's 500), however small the mob. */
	private static void applyHealthFloor(LivingEntity e, double minHealth) {
		Attrs.remove(e, Attributes.MAX_HEALTH, BossKeys.MOD_HEALTH_FLOOR);
		double max = e.getMaxHealth();
		if (minHealth <= 0 || max <= 0 || max >= minHealth) {
			return;
		}
		Attrs.set(e, Attributes.MAX_HEALTH, BossKeys.MOD_HEALTH_FLOOR, minHealth / max - 1.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	}

	// =====================================================================
	// Tracking + ticker
	// =====================================================================

	private void register(Boss boss) {
		active.put(boss.uuid(), boss);
		bosses.bossBars().create(boss);
	}

	public void unregister(Boss boss) {
		if (active.remove(boss.uuid()) == null) {
			return;
		}
		glow(boss, false);
		bosses.bossBars().remove(boss);
	}

	/** The entity is gone (unloaded, despawned, removed): forget it. Deaths are handled by onDeath first. */
	public void onUnload(Entity entity) {
		Boss boss = active.get(entity.getUUID());
		if (boss != null && boss.entity() == entity) {
			bosses.nemesis().onUnload(boss);
			unregister(boss);
		}
	}

	private static boolean valid(LivingEntity e) {
		return e.isAlive() && !e.isRemoved() && e.level() instanceof ServerLevel level && level.getEntity(e.getUUID()) == e;
	}

	/** Every 10 ticks, for every loaded boss. */
	public void tick() {
		if (active.isEmpty()) {
			return;
		}
		int now = Ticks.now();
		int timeout = settings().combatTimeoutTicks;
		for (Boss boss : List.copyOf(active.values())) {
			LivingEntity e = boss.entity();
			if (!valid(e)) {
				unregister(boss);
				continue;
			}
			// A boss chasing a player keeps that player "in combat" even before the first hit lands.
			if (e instanceof Mob mob && mob.getTarget() instanceof ServerPlayer chased && boss.fightable(chased)
				&& chased.distanceToSqr(e) <= settings().bossbarViewDistance * settings().bossbarViewDistance) {
				engage(boss, chased);
			}
			if (bosses.nemesis().tickActive(boss)) {
				continue; // the Nemesis withdrew
			}
			boolean gone = false;
			for (UUID fled : boss.expireEngagements(now, timeout)) {
				if (bosses.nemesis().onPlayerFled(boss, fled)) {
					gone = true;
					break;
				}
			}
			if (gone) {
				continue;
			}
			checkStuck(boss, now);
			glow(boss, playerWithin(boss, settings().glowWithin));
			bosses.bossBars().refreshViewers(boss);
			for (BossTrait trait : boss.traits()) {
				try {
					trait.onTick(boss, now);
				} catch (RuntimeException ex) {
					BloomCraft.LOG.warn("Trait {} failed on tick", trait.id(), ex);
				}
			}
			if (now % 20 < 10) {
				bosses.presentation().ambient(boss);
			}
		}
	}

	// =====================================================================
	// Glow: a boss lights up (in its rank's colour) while someone is close
	// =====================================================================

	private static boolean playerWithin(Boss boss, double radius) {
		if (radius <= 0) {
			return false;
		}
		for (ServerPlayer p : boss.level().players()) {
			if (!p.isSpectator() && p.distanceToSqr(boss.entity()) <= radius * radius) {
				return true;
			}
		}
		return false;
	}

	private void glow(Boss boss, boolean on) {
		LivingEntity e = boss.entity();
		if (e.hasGlowingTag() == on || (on && !valid(e))) {
			return;
		}
		e.setGlowingTag(on);
		if (settings().glowRankColors) {
			Teams.setGlowColor(e, on ? boss.rank() : null);
		}
	}

	// =====================================================================
	// Two strikes: a boss that has killed enough players leaves
	// =====================================================================

	/** A boss (or its minion) killed a player. */
	public void onKilledPlayer(Boss boss, ServerPlayer victim) {
		LivingEntity e = boss.entity();
		CompoundTag data = Data.of(e);
		int kills = data.getIntOr(BossKeys.PLAYER_KILLS, 0) + 1;
		data.putInt(BossKeys.PLAYER_KILLS, kills);
		boss.addVictim(victim.getUUID());
		if (bosses.nemesis().onKilledPlayer(boss, victim, kills)) {
			return; // it is (or just became) a Nemesis: it handles its own leaving
		}
		int limit = settings().despawnAfterKills;
		if (limit > 0 && kills >= limit) {
			bosses.later(1, () -> {
				if (active.get(boss.uuid()) == boss && valid(e)) {
					Fx.particle(boss.level(), ParticleTypes.LARGE_SMOKE, Fx.center(e), 30, 0.6, 0.05);
					removeMinions(boss);
					unregister(boss);
					e.discard();
				}
			});
		}
	}

	public void engage(Boss boss, ServerPlayer player) {
		if (player.isSpectator()) {
			return;
		}
		boolean first = !boss.engaged().containsKey(player.getUUID());
		int now = Ticks.now();
		boss.engage(player, now);
		if (first || boss.ready("threat", now)) {
			boss.cooldown("threat", now, 100); // re-counted every few seconds: gear put on mid-fight counts too
			applyThreat(boss);
		}
		if (first) {
			// Once a fight has started, persistent ranks stop despawning: a boss never vanishes because you
			// stepped back to heal. Bosses nobody has fought still despawn normally.
			if (settings().rank(boss.rank()).persistent() && boss.entity() instanceof Mob mob) {
				mob.setPersistenceRequired();
			}
			bosses.presentation().reveal(boss, player);
			bosses.bossBars().refreshViewers(boss);
		}
	}

	// =====================================================================
	// Threat Scaling
	// =====================================================================

	public static double threat(LivingEntity e) {
		return Data.dbl(e, BossKeys.THREAT, 0.0);
	}

	/** The Threat the reward bonus uses: as it was when the fight began. */
	public static double rewardThreat(LivingEntity e) {
		return Data.dbl(e, BossKeys.THREAT_REWARD, threat(e));
	}

	/** The boss counts the Boss Gear the players nearby actually use, and gains health and damage to match. */
	private void applyThreat(Boss boss) {
		BossSettings f = settings();
		LivingEntity e = boss.entity();
		if (!f.threatEnabled) {
			return;
		}
		double best = 0;
		for (ServerPlayer p : boss.nearbyPlayers(f.threatRadius)) {
			best = Math.max(best, bossGearInUse(p));
		}
		double threat = Math.max(0, Math.min(1, best / f.threatFullPieces));
		double old = threat(e);
		CompoundTag data = Data.of(e);
		// it only ever goes up during a fight
		if (data.contains(BossKeys.THREAT) && threat <= old + 0.01) {
			return;
		}
		threat = Math.max(threat, old);
		data.putDouble(BossKeys.THREAT, threat);
		if (!data.contains(BossKeys.THREAT_REWARD)) {
			// the reward bonus is set by the gear worn when the fight began
			data.putDouble(BossKeys.THREAT_REWARD, threat);
		}
		if (threat <= 0.01) {
			return;
		}
		double ratio = boss.healthRatio();
		Attrs.set(e, Attributes.MAX_HEALTH, BossKeys.MOD_THREAT, threat * f.threatMaxHealth / 100.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		e.setHealth((float) Math.max(1, boss.maxHealth() * ratio));
		boss.scaleDamage((1.0 + threat * f.threatMaxDamage / 100.0) / (1.0 + old * f.threatMaxDamage / 100.0));
		bosses.bossBars().updateHealth(boss);
	}

	/** How many pieces of Boss Gear a player wears or holds. Their bag doesn't count. */
	public int bossGearInUse(ServerPlayer p) {
		int n = 0;
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND}) {
			if (BossItems.isGear(p.getItemBySlot(slot))) {
				n++;
			}
		}
		return n;
	}

	// =====================================================================
	// Promotion (Souls) + Ascendant phases
	// =====================================================================

	/** Raises a living boss by some ranks (it heals to full and gains traits to match). Null if it can't rise. */
	public @Nullable Boss promote(Boss boss, int steps, @Nullable ServerPlayer by) {
		BossRank target = boss.rank().up(steps);
		LivingEntity e = boss.entity();
		if (boss.isNemesis() || target == boss.rank() || !valid(e)) {
			return null;
		}
		List<BossTrait> traits = new ArrayList<>(boss.traits());
		RankSettings rs = settings().rank(target);
		int want = Rng.between(rs.traits().min(), rs.traits().max());
		while (traits.size() < want) {
			BossTrait extra = bosses.traits().rollExtra(e, traits);
			if (extra == null) {
				break;
			}
			traits.add(extra);
		}
		List<UUID> fighters = new ArrayList<>(boss.engaged().keySet());
		Set<UUID> minions = new HashSet<>(boss.minions());
		CompoundTag data = Data.of(e);
		if (!data.contains(BossKeys.WILD_RANK)) {
			data.putString(BossKeys.WILD_RANK, boss.rank().name());
		}
		boolean undying = boss.undyingUsed();
		unregister(boss);
		data.remove(BossKeys.LAST_STAND);
		data.remove(BossKeys.PHASE);
		double threat = threat(e);
		Boss promoted = createBoss(e, target, boss.categoryId(), traits, false, new HashSet<>(boss.traitIds()));
		if (threat > 0.01) {
			Attrs.set(e, Attributes.MAX_HEALTH, BossKeys.MOD_THREAT, threat * settings().threatMaxHealth / 100.0,
				AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
			e.setHealth((float) promoted.maxHealth());
		}
		promoted.minions().addAll(minions);
		promoted.setUndyingUsed(undying);
		MinecraftServer server = promoted.level().getServer();
		for (UUID id : fighters) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p != null) {
				engage(promoted, p);
			}
		}
		if (by != null) {
			engage(promoted, by);
		}
		ServerLevel level = promoted.level();
		Presentation.lightningEffect(level, e.position());
		Fx.dust(level, Fx.center(e), target.rgb(), 2.2f, 60, 1.0);
		Fx.particle(level, ParticleTypes.END_ROD, Fx.center(e), 40, 0.8, 0.1);
		Fx.play(e, "block.beacon.power_select", 1.0f, 0.7f);
		bosses.presentation().announcePromotion(promoted, by);
		if (by != null) {
			QuestFlags.mark(by, "soul_promote");
			if (target == BossRank.ASCENDANT) {
				QuestFlags.mark(by, "ascendant_created");
			}
		}
		return promoted;
	}

	/** Ascendant bosses break into a new phase at set health marks: a shockwave and a new trait. */
	public void checkPhases(Boss boss) {
		BossSettings f = settings();
		double health = boss.health();
		if (boss.rank() != BossRank.ASCENDANT || !f.ascendantPhases || health <= 0) {
			return;
		}
		LivingEntity e = boss.entity();
		CompoundTag data = Data.of(e);
		int passed = data.getIntOr(BossKeys.PHASE, 0);
		if (passed >= f.ascendantPhaseThresholds.size()
			|| health / boss.maxHealth() * 100.0 > f.ascendantPhaseThresholds.get(passed)) {
			return;
		}
		data.putInt(BossKeys.PHASE, passed + 1);
		e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 50, 3, false, false));
		double radius = f.ascendantShockwaveRadius;
		for (ServerPlayer p : boss.nearbyPlayers(radius)) {
			AreaDamage.hit(p, f.ascendantShockwaveDamage * boss.power(), e);
			Vec3 away = Motion.flatDirection(e.position(), p.position()).scale(1.4);
			Motion.set(p, new Vec3(away.x, 0.6, away.z));
		}
		BossTrait extra = bosses.traits().rollExtra(e, boss.traits());
		if (extra != null) {
			boss.addTrait(extra);
			extra.onApply(boss, true);
			saveTraits(boss);
		}
		Fx.play(e, "entity.warden.sonic_boom", 1.0f, 0.8f);
		Fx.particle(boss.level(), ParticleTypes.SONIC_BOOM, Fx.center(e), 1, 0, 0);
		Fx.particle(boss.level(), ParticleTypes.END_ROD, e.position().add(0, 0.2, 0), 80, radius / 2.0, 0.05);
		bosses.presentation().messageNearby(boss, "ascendant-phase", 48);
	}

	/** Purple and Gold bosses (and every Nemesis) make a Last Stand once at low health. */
	public void checkLastStand(Boss boss) {
		BossSettings f = settings();
		double health = boss.health();
		if (boss.lastStand() || health <= 0) {
			return;
		}
		if (!boss.isNemesis() && !f.lastStandRanks.contains(boss.rank())) {
			return;
		}
		if (health / boss.maxHealth() * 100.0 > f.lastStandHealthPercent) {
			return;
		}
		LivingEntity e = boss.entity();
		boss.setLastStand(true);
		Data.of(e).putBoolean(BossKeys.LAST_STAND, true);
		boss.scalePower(1.0 + f.lastStandPowerBonus / 100.0);
		if (f.lastStandDormantTrait) {
			BossTrait extra = bosses.traits().rollExtra(e, boss.traits());
			if (extra != null) {
				boss.addTrait(extra);
				extra.onApply(boss, true);
				saveTraits(boss);
			}
		}
		e.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 1, false, false));
		Fx.play(e, "entity.ender_dragon.growl", 0.9f, 0.8f);
		Fx.dust(boss.level(), Fx.center(e), boss.rank().rgb(), 2.0f, 40, 1.0);
		Fx.particle(boss.level(), ParticleTypes.SOUL_FIRE_FLAME, Fx.center(e), 40, 0.8, 0.05);
		bosses.presentation().messageNearby(boss, "last-stand", 40);
	}

	public void saveTraits(Boss boss) {
		Data.strings(Data.of(boss.entity()), BossKeys.TRAITS, boss.traitIds());
	}

	// =====================================================================
	// Death + rewards
	// =====================================================================

	/** After the boss died (its normal drops are already out). */
	public void handleDeath(Boss boss) {
		LivingEntity e = boss.entity();
		ServerLevel level = boss.level();
		ServerPlayer killer = e.getLastHurtByPlayer() instanceof ServerPlayer sp ? sp : null;
		boolean killedByPlayer = killer != null;
		if (killer == null) {
			killer = boss.lastEngagedPlayer();
		}
		RankSettings rs = settings().rank(boss.rank());
		BossSettings f = settings();
		boolean eligible = !f.requirePlayerForRewards || killer != null;
		// Quest Book: everyone who actually hurt it took down a boss
		MinecraftServer server = level.getServer();
		for (UUID fighter : boss.engaged().keySet()) {
			if (boss.damageBy(fighter) > 0 && (killer == null || !fighter.equals(killer.getUUID()))) {
				ServerPlayer p = server.getPlayerList().getPlayer(fighter);
				if (p != null) {
					QuestFlags.mark(p, "boss_kill");
				}
			}
		}
		if (killer != null && (killedByPlayer || boss.damageBy(killer.getUUID()) > 0)) {
			QuestFlags.mark(killer, "boss_kill");
		}

		if (eligible) {
			int baseXp = e.getExperienceReward(level, killer);
			double xp = baseXp * rs.xpMultiplier() + rs.xpBonus();
			List<ItemStack> rewards = new ArrayList<>(bosses.rewards().rollWithFloor(boss, killer));

			// Revenge: this boss killed the player who finally brought it down.
			boolean revenge = killer != null && boss.killed(killer.getUUID());
			if (revenge) {
				xp *= f.nemesisRevengeXpMultiplier;
				rewards.addAll(bosses.rewards().roll(boss, killer));
				bosses.presentation().tell(killer, "revenge");
			}
			// (worked out before onSlain, which ends the Nemesis's record)
			BossRank wild = boss.isNemesis() ? bosses.nemesis().soulRank(boss) : wildRank(e, boss.rank());
			if (boss.isNemesis()) {
				bosses.nemesis().onSlain(boss, killer, rewards);
			}
			// Bosses players made don't drop Souls or Totems, so they can't feed themselves. A Nemesis always
			// drops Souls; one called by its Challenge just no Totem.
			String created = created(e);
			boolean soulsOk = boss.isNemesis() || created == null;
			boolean totemOk = boss.isNemesis() ? !"challenge".equals(created) : created == null;
			// A wild boss raised with Souls pays Souls and Totems for the rank it was found at.
			if (f.soulsEnabled && soulsOk && (boss.isNemesis() || Rng.chance(f.soulChance.getOrDefault(wild, 0.0)))) {
				int[] count = f.soulCount.getOrDefault(wild, new int[]{1, 1});
				rewards.add(BossItems.soul(wild, Rng.between(count[0], count[1])));
			}
			if (f.waystonesEnabled) {
				int stones = boss.rank() == BossRank.ASCENDANT ? f.waystoneAscendantDrops : 0;
				if (Rng.chance(f.waystoneDropChance.getOrDefault(boss.rank(), 0.0))) {
					stones++;
				}
				for (int i = 0; i < stones; i++) {
					rewards.add(BossItems.waystone());
				}
			}
			if (f.totemEnabled && totemOk && Rng.chance(f.totemDropChance.getOrDefault(wild, 0.0))) {
				// a Totem of its own rank (sometimes one higher), up to Legendary
				BossRank called = wild.atLeast(BossRank.GOLD) ? BossRank.GOLD : wild;
				if (called != BossRank.GOLD && Rng.chance(f.totemHigherChance)) {
					called = called.up(1);
				}
				rewards.add(BossItems.totem(called));
			}
			// Boss Gear salvages into the same Soul this boss could drop (summoned bosses' gear into none).
			for (ItemStack item : rewards) {
				BossItems.setSalvageRank(item, soulsOk ? wild : null);
			}
			dropRewards(boss, rewards);
			int extraXp = (int) Math.round(xp) - baseXp;
			if (extraXp > 0) {
				ExperienceOrb.award(level, e.position(), extraXp);
			}
			if (killer != null) {
				CompoundTag pd = Data.of(killer);
				pd.putInt(BossKeys.kills(boss.rank()), pd.getIntOr(BossKeys.kills(boss.rank()), 0) + 1);
				bosses.escalation().recordKill(killer);
				if (!rewards.isEmpty() && f.bossChat) {
					MutableComponent list = Component.empty();
					for (int i = 0; i < rewards.size(); i++) {
						if (i > 0) {
							list.append(Component.literal(", "));
						}
						list.append(rewards.get(i).getDisplayName());
					}
					killer.sendSystemMessage(f.messages.prefixed("rewards", Map.of("items", list)));
				}
			}
		} else if (boss.isNemesis()) {
			bosses.nemesis().onSlain(boss, null, new ArrayList<>());
		}

		for (BossTrait trait : boss.traits()) {
			trait.onDeath(boss);
		}
		bosses.presentation().death(boss, killer);
		removeMinions(boss);
		unregister(boss);
	}

	// =====================================================================
	// Anti-trap
	// =====================================================================

	/** Pulls bosses out of vehicles and frees them if they can't reach their target for a while. */
	private void checkStuck(Boss boss, int now) {
		BossSettings f = settings();
		LivingEntity e = boss.entity();
		if (f.blockVehicles && e.isPassenger()) {
			e.stopRiding();
		}
		if (!f.unstuckEnabled || !BaseTrait.walks(e)) {
			return;
		}
		ServerPlayer target = boss.target();
		boolean wantsToMove = false;
		if (target != null) {
			double dist = target.distanceTo(e);
			boolean ranged = e instanceof RangedAttackMob;
			// Archers standing still and shooting are fine; only count them stuck when they can't see you.
			wantsToMove = dist > 3.5 && dist < 48 && (!ranged || !e.hasLineOfSight(target));
		}
		if (boss.stuckTicks(now, wantsToMove) < f.unstuckTicks || target == null) {
			return;
		}
		boss.resetStuck();
		SafeSpots.Spot spot = SafeSpots.behind(target, 2.5);
		ServerLevel level = boss.level();
		if (spot != null) {
			Fx.particle(level, ParticleTypes.PORTAL, Fx.center(e), 30, 0.5, 0.4);
			e.snapTo(spot.pos().x, spot.pos().y, spot.pos().z, spot.yaw(), e.getXRot());
			Fx.particle(level, ParticleTypes.PORTAL, Fx.center(e), 30, 0.5, 0.4);
			Fx.play(level, spot.pos(), "entity.enderman.teleport", 1.0f, 0.7f);
		} else {
			// Nowhere to stand near the player (a 1x1 pillar): drag the player down instead.
			Vec3 pull = e.position().subtract(target.position());
			if (pull.lengthSqr() > 0.01) {
				Vec3 v = pull.normalize().scale(1.1);
				Motion.set(target, new Vec3(v.x, 0.2, v.z));
			}
			Fx.play(target, "entity.warden.sonic_charge", 0.8f, 1.2f);
		}
		bosses.presentation().messageNearby(boss, "unstuck", 32);
	}

	private void dropRewards(Boss boss, List<ItemStack> rewards) {
		ServerLevel level = boss.level();
		Vec3 at = boss.entity().position().add(0, 0.5, 0);
		for (ItemStack reward : rewards) {
			ItemEntity drop = new ItemEntity(level, at.x, at.y, at.z, reward,
				Rng.between(-0.1, 0.1), 0.25, Rng.between(-0.1, 0.1));
			drop.setGlowingTag(true);
			drop.setUnlimitedLifetime();
			Data.flag(drop, BossKeys.PROTECTED_DROP, true); // can't burn, blow up or be destroyed (ItemEntityMixin)
			level.addFreshEntity(drop);
		}
	}

	public void removeMinions(Boss boss) {
		ServerLevel level = boss.level();
		for (UUID id : boss.minions()) {
			Entity minion = level.getEntity(id);
			if (minion != null && minion.isAlive()) {
				Fx.particle(level, ParticleTypes.SMOKE, Fx.center(minion), 10, 0.3, 0.02);
				minion.discard();
			}
		}
		boss.minions().clear();
	}

	// =====================================================================
	// Admin / shutdown
	// =====================================================================

	public int killAll() {
		int count = 0;
		for (Boss boss : List.copyOf(active.values())) {
			removeMinions(boss);
			unregister(boss);
			boss.entity().discard();
			count++;
		}
		return count;
	}

	public void shutdown() {
		for (Boss boss : List.copyOf(active.values())) {
			glow(boss, false);
			bosses.bossBars().remove(boss);
		}
		active.clear();
	}

	/** Mob type id for messages: "minecraft:zombie" -> "Zombie". */
	public static String mobName(EntityType<?> type) {
		return Text.pretty(BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath());
	}

	/** Shared helper: the player inventory's armor + hands (for counting gear). */
	public static List<ItemStack> worn(ServerPlayer p) {
		Inventory inv = p.getInventory();
		List<ItemStack> out = new ArrayList<>();
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			if (slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR || slot.getType() == EquipmentSlot.Type.HAND) {
				out.add(p.getItemBySlot(slot));
			}
		}
		return out;
	}
}
