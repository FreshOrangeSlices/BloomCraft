package com.bloomcraft.bosses;

import com.bloomcraft.bosses.trait.BossTrait;
import com.bloomcraft.core.Ticks;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runtime state of one loaded boss. Its permanent identity (rank, traits, category) is saved on the entity
 * (see {@link BossKeys}); this is the in-memory view while it's loaded.
 */
public final class Boss {

	private final UUID uuid;
	private final LivingEntity entity;
	private final BossRank rank;
	private final String categoryId;
	private final List<BossTrait> traits;
	private Component name;
	private String plainName;
	private double damageMultiplier;
	private double traitPower;
	private @Nullable String nemesisId;
	private boolean lastStand;
	private @Nullable Vec3 lastPosition;
	private int stuckSince = -1;

	/** Player UUID -> tick of their last interaction with this boss. */
	private final Map<UUID, Integer> engaged = new HashMap<>();
	private final Set<UUID> barViewers = new HashSet<>();
	private final Set<UUID> revealedTo = new HashSet<>();
	private final Set<UUID> minions = new HashSet<>();
	private final Map<String, Integer> cooldowns = new HashMap<>();
	private final Set<String> flags = new HashSet<>();
	private @Nullable ServerBossEvent bar;
	private boolean undyingUsed;
	private boolean revealed;
	private int lastHurtTick;

	public Boss(LivingEntity entity, BossRank rank, String categoryId, List<BossTrait> traits, Component name,
				String plainName, double damageMultiplier, double traitPower) {
		this.uuid = entity.getUUID();
		this.entity = entity;
		this.rank = rank;
		this.categoryId = categoryId;
		this.traits = new CopyOnWriteArrayList<>(traits);
		this.name = name;
		this.plainName = plainName;
		this.damageMultiplier = damageMultiplier;
		this.traitPower = traitPower;
	}

	public UUID uuid() {
		return uuid;
	}

	public LivingEntity entity() {
		return entity;
	}

	public ServerLevel level() {
		return (ServerLevel) entity.level();
	}

	public BossRank rank() {
		return rank;
	}

	public String categoryId() {
		return categoryId;
	}

	public List<BossTrait> traits() {
		return Collections.unmodifiableList(traits);
	}

	/** Adds a trait mid-fight (Last Stand). Safe while traits are being iterated. */
	public void addTrait(BossTrait trait) {
		if (!hasTrait(trait.id())) {
			traits.add(trait);
		}
	}

	public boolean hasTrait(String id) {
		for (BossTrait trait : traits) {
			if (trait.id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	public List<String> traitIds() {
		List<String> ids = new ArrayList<>();
		for (BossTrait t : traits) {
			ids.add(t.id());
		}
		return ids;
	}

	public Component name() {
		return name;
	}

	public String plainName() {
		return plainName;
	}

	public void rename(Component name, String plainName) {
		this.name = name;
		this.plainName = plainName;
		entity.setCustomName(name);
	}

	/** Multiplies the boss's own damage (Threat Scaling). */
	public void scaleDamage(double factor) {
		this.damageMultiplier *= factor;
	}

	/** Multiplies the boss's trait power (Last Stand). */
	public void scalePower(double factor) {
		this.traitPower *= factor;
	}

	private double nemesisDamage = 1.0;
	private double nemesisPower = 1.0;
	private double nemesisDamageCap = Double.MAX_VALUE;

	/** Nemesis level scaling, kept apart from the boss's own numbers so it doesn't compound. */
	public void setNemesisScaling(double damageFactor, double powerFactor, double damageCap) {
		this.nemesisDamage = damageFactor;
		this.nemesisPower = powerFactor;
		this.nemesisDamageCap = damageCap;
	}

	public @Nullable String nemesisId() {
		return nemesisId;
	}

	public void setNemesisId(@Nullable String nemesisId) {
		this.nemesisId = nemesisId;
	}

	public boolean isNemesis() {
		return nemesisId != null;
	}

	public boolean lastStand() {
		return lastStand;
	}

	public void setLastStand(boolean lastStand) {
		this.lastStand = lastStand;
	}

	/** Rank (and mob profile) damage multiplier applied to everything this boss hits. */
	public double damageMultiplier() {
		if (nemesisDamage == 1.0) {
			return damageMultiplier;
		}
		return Math.min(Math.max(damageMultiplier, nemesisDamageCap), damageMultiplier * nemesisDamage);
	}

	/** How strong traits are for this boss (1.0 = base). */
	public double power() {
		if (nemesisPower == 1.0) {
			return traitPower;
		}
		return Math.min(Math.max(traitPower, 4.0), traitPower * nemesisPower);
	}

	public double health() {
		return entity.getHealth();
	}

	public double maxHealth() {
		return Math.max(1, entity.getMaxHealth());
	}

	public double healthRatio() {
		return Math.max(0, Math.min(1, health() / maxHealth()));
	}

	public void heal(double amount) {
		if (amount <= 0 || !entity.isAlive()) {
			return;
		}
		entity.setHealth((float) Math.min(maxHealth(), entity.getHealth() + amount));
	}

	// ---------------- stuck detection ----------------

	/** How many ticks the boss has barely moved while it had a reason to move, or -1 when it's moving fine. */
	public int stuckTicks(int now, boolean wantsToMove) {
		Vec3 here = entity.position();
		if (!wantsToMove) {
			stuckSince = -1;
			lastPosition = here;
			return -1;
		}
		if (lastPosition == null || lastPosition.distanceToSqr(here) > 1.0) {
			lastPosition = here;
			stuckSince = now;
			return 0;
		}
		if (stuckSince < 0) {
			stuckSince = now;
		}
		return now - stuckSince;
	}

	public void resetStuck() {
		stuckSince = -1;
		lastPosition = null;
	}

	// ---------------- engagement ----------------

	public void engage(ServerPlayer player, int now) {
		engaged.put(player.getUUID(), now);
	}

	public Map<UUID, Integer> engaged() {
		return engaged;
	}

	/** Removes players who left combat and returns them (to detect players fleeing a boss). */
	public List<UUID> expireEngagements(int now, int timeoutTicks) {
		List<UUID> expired = new ArrayList<>();
		Iterator<Map.Entry<UUID, Integer>> it = engaged.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> e = it.next();
			if (now - e.getValue() > timeoutTicks) {
				expired.add(e.getKey());
				it.remove();
			}
		}
		return expired;
	}

	// ---------------- per-player fight history (Nemesis escape + adaptation) ----------------

	private final Map<UUID, double[]> fightLog = new HashMap<>(); // [total damage, melee hits, ranged hits]
	private final Set<UUID> victims = new HashSet<>();

	private record RecentHit(int tick, UUID player, double damage) {
	}

	/** Hits from the last 10 seconds (team aggro). */
	private static final int RECENT_TICKS = 200;
	private final ArrayDeque<RecentHit> recentHits = new ArrayDeque<>();

	public void recordPlayerHit(UUID player, double damage, boolean projectile) {
		double[] log = fightLog.computeIfAbsent(player, k -> new double[3]);
		log[0] += damage;
		log[projectile ? 2 : 1]++;
		int now = Ticks.now();
		recentHits.addLast(new RecentHit(now, player, damage));
		while (!recentHits.isEmpty() && now - recentHits.peekFirst().tick() > RECENT_TICKS) {
			recentHits.pollFirst();
		}
	}

	public double recentDamage(UUID player, int now) {
		double sum = 0;
		for (RecentHit hit : recentHits) {
			if (now - hit.tick() <= RECENT_TICKS && hit.player().equals(player)) {
				sum += hit.damage();
			}
		}
		return sum;
	}

	public @Nullable UUID topRecentDamager(int now) {
		Map<UUID, Double> totals = new HashMap<>();
		for (RecentHit hit : recentHits) {
			if (now - hit.tick() <= RECENT_TICKS) {
				totals.merge(hit.player(), hit.damage(), Double::sum);
			}
		}
		UUID best = null;
		double bestDamage = 0;
		for (Map.Entry<UUID, Double> e : totals.entrySet()) {
			if (e.getValue() > bestDamage) {
				bestDamage = e.getValue();
				best = e.getKey();
			}
		}
		return best;
	}

	public double damageBy(UUID player) {
		double[] log = fightLog.get(player);
		return log == null ? 0 : log[0];
	}

	/** {melee hits, ranged hits} this player landed on the boss. */
	public int[] hitStyle(UUID player) {
		double[] log = fightLog.get(player);
		return log == null ? new int[2] : new int[]{(int) log[1], (int) log[2]};
	}

	public void forgetFight(UUID player) {
		fightLog.remove(player);
	}

	public void addVictim(UUID player) {
		victims.add(player);
	}

	public boolean killed(UUID player) {
		return victims.contains(player);
	}

	public boolean inCombat() {
		return !engaged.isEmpty();
	}

	/** Most recently engaged online player, if any. */
	public @Nullable ServerPlayer lastEngagedPlayer() {
		UUID best = null;
		int bestTick = Integer.MIN_VALUE;
		for (Map.Entry<UUID, Integer> e : engaged.entrySet()) {
			if (e.getValue() > bestTick) {
				bestTick = e.getValue();
				best = e.getKey();
			}
		}
		return best == null ? null : level().getServer().getPlayerList().getPlayer(best);
	}

	/**
	 * The player this boss is fighting: its AI target if that's a player, otherwise the most recently engaged
	 * player within 24 blocks. Creative and spectator players are ignored.
	 */
	public @Nullable ServerPlayer target() {
		if (entity instanceof Mob mob && mob.getTarget() instanceof ServerPlayer p && fightable(p)) {
			return p;
		}
		ServerPlayer last = lastEngagedPlayer();
		if (last != null && fightable(last) && last.distanceToSqr(entity) <= 24 * 24) {
			return last;
		}
		return null;
	}

	public List<ServerPlayer> nearbyPlayers(double radius) {
		List<ServerPlayer> out = new ArrayList<>();
		for (ServerPlayer p : level().players()) {
			if (fightable(p) && p.distanceToSqr(entity) <= radius * radius) {
				out.add(p);
			}
		}
		return out;
	}

	public boolean fightable(ServerPlayer p) {
		return p.isAlive() && !p.isRemoved() && p.level() == entity.level() && !p.isCreative() && !p.isSpectator();
	}

	// ---------------- per-trait state ----------------

	public boolean ready(String key, int now) {
		Integer until = cooldowns.get(key);
		return until == null || now >= until;
	}

	public void cooldown(String key, int now, int ticks) {
		cooldowns.put(key, now + ticks);
	}

	public int cooldownUntil(String key) {
		return cooldowns.getOrDefault(key, 0);
	}

	public boolean flag(String key) {
		return flags.contains(key);
	}

	public void setFlag(String key, boolean value) {
		if (value) {
			flags.add(key);
		} else {
			flags.remove(key);
		}
	}

	public void markHurt(int now) {
		lastHurtTick = now;
	}

	public int lastHurtTick() {
		return lastHurtTick;
	}

	public boolean undyingUsed() {
		return undyingUsed;
	}

	public void setUndyingUsed(boolean undyingUsed) {
		this.undyingUsed = undyingUsed;
	}

	public Set<UUID> minions() {
		return minions;
	}

	// ---------------- presentation ----------------

	public @Nullable ServerBossEvent bar() {
		return bar;
	}

	public void bar(@Nullable ServerBossEvent bar) {
		this.bar = bar;
	}

	public Set<UUID> barViewers() {
		return barViewers;
	}

	/** True the first time a given player engages; used for the one-time encounter cue. */
	public boolean revealTo(ServerPlayer player) {
		return revealedTo.add(player.getUUID());
	}

	/** True the first time anyone engages; used for the one-time burst of particles. */
	public boolean revealOnce() {
		if (revealed) {
			return false;
		}
		revealed = true;
		return true;
	}
}
