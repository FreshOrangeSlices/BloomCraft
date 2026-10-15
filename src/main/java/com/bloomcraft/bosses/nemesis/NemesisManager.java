package com.bloomcraft.bosses.nemesis;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossArmor;
import com.bloomcraft.bosses.BossKeys;
import com.bloomcraft.bosses.BossManager;
import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.config.BossSettings;
import com.bloomcraft.bosses.config.Messages;
import com.bloomcraft.bosses.config.RankSettings;
import com.bloomcraft.bosses.item.BossItems;
import com.bloomcraft.bosses.item.ModItems;
import com.bloomcraft.bosses.trait.BossTrait;
import com.bloomcraft.bosses.trait.Synergies;
import com.bloomcraft.core.Attrs;
import com.bloomcraft.core.Clock;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.QuestFlags;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.SafeSpots;
import com.bloomcraft.core.Spawns;
import com.bloomcraft.core.Text;
import com.bloomcraft.core.Ticks;
import com.bloomcraft.core.WorldStore;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Container;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.cubemob.AbstractCubeMob;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The Nemesis system. A boss that kills a player (or that a player flees from after a real fight) becomes that
 * player's Nemesis: it withdraws, remembers everything, grows stronger, earns titles and counter-traits, and comes
 * back for its prey after a few Minecraft days. It ignores the normal boss cap and never despawns while active.
 */
public final class NemesisManager {

	private static final long DAY = Clock.DAY;
	private static final String STORE = "nemeses";

	private final Bosses bosses;
	private final Map<String, NemesisRecord> records = new LinkedHashMap<>();
	/** When a boss (or its minion) last hurt each player: a death it set up is never a surrender. */
	private final Map<UUID, Integer> lastBossHit = new HashMap<>();

	public NemesisManager(Bosses bosses) {
		this.bosses = bosses;
	}

	private BossSettings f() {
		return bosses.settings();
	}

	private Messages messages() {
		return bosses.settings().messages;
	}

	/** "Minecraft time" used for return timers (see {@link Clock}). */
	public long clock() {
		return Clock.now();
	}

	private static @Nullable MinecraftServer server() {
		return Ticks.server();
	}

	private static @Nullable ServerPlayer online(@Nullable UUID id) {
		MinecraftServer server = server();
		return server == null || id == null ? null : server.getPlayerList().getPlayer(id);
	}

	private static Map<String, Component> boss(Component name) {
		return Map.of("boss", name);
	}

	// =====================================================================
	// Storage
	// =====================================================================

	/** Server started: reads the world's Nemeses. */
	public void load() {
		records.clear();
		lastBossHit.clear();
		CompoundTag root = WorldStore.section(STORE);
		for (String id : root.keySet()) {
			NemesisRecord r = NemesisRecord.load(id, root.getCompoundOrEmpty(id));
			if (r != null) {
				records.put(id, r);
			} else {
				BloomCraft.LOG.warn("Skipping unreadable Nemesis {}", id);
			}
		}
		boolean fixed = false;
		for (NemesisRecord r : records.values()) {
			if (r.level > f().nemesisMaxLevel) {
				r.level = f().nemesisMaxLevel;
				updateTitles(r);
				fixed = true;
			}
			if (r.name.isBlank()) {
				r.name = NemesisNames.pick(r.type, takenNames());
				fixed = true;
			}
			if (r.epithet.isBlank()) {
				BossTrait first = r.traits.isEmpty() ? null : bosses.traits().get(r.traits.getFirst());
				r.epithet = first != null ? first.adjective() : f().rank(r.baseRank).name();
				fixed = true;
			}
		}
		if (fixed) {
			save();
		}
	}

	private Set<String> takenNames() {
		Set<String> taken = new HashSet<>();
		for (NemesisRecord r : records.values()) {
			if (!r.name.isBlank()) {
				taken.add(r.name.toLowerCase(Locale.ROOT));
			}
		}
		return taken;
	}

	/** Writes the records into the world store (the file itself is written when the world saves). */
	public void save() {
		CompoundTag root = new CompoundTag();
		for (NemesisRecord r : records.values()) {
			root.put(r.id, r.save());
		}
		WorldStore.put(STORE, root);
	}

	/** Server stopping: active Nemeses aren't saved in the world, so they will return a little later instead. */
	public void shutdown() {
		for (NemesisRecord r : records.values()) {
			if (r.entity != null) {
				Entity living = find(r.entity);
				if (living != null) {
					living.discard();
				}
				r.entity = null;
				r.prowlUntil = 0;
				r.returnAt = Math.max(r.returnAt, clock() + DAY / 4);
			}
		}
		save();
		records.clear();
	}

	private static @Nullable Entity find(@Nullable UUID id) {
		MinecraftServer server = server();
		if (server == null || id == null) {
			return null;
		}
		for (ServerLevel level : server.getAllLevels()) {
			Entity e = level.getEntity(id);
			if (e != null) {
				return e;
			}
		}
		return null;
	}

	public int count() {
		return records.size();
	}

	public List<NemesisRecord> all() {
		return List.copyOf(records.values());
	}

	public List<NemesisRecord> forOwner(UUID owner) {
		List<NemesisRecord> out = new ArrayList<>();
		for (NemesisRecord r : records.values()) {
			if (r.owner.equals(owner)) {
				out.add(r);
			}
		}
		return out;
	}

	public @Nullable NemesisRecord get(@Nullable String id) {
		return id == null ? null : records.get(id);
	}

	public @Nullable NemesisRecord of(Boss boss) {
		return get(boss.nemesisId());
	}

	/** The owner this boss hunts (null if it isn't a Nemesis). */
	public @Nullable UUID owner(Boss boss) {
		NemesisRecord r = of(boss);
		return r == null ? null : r.owner;
	}

	public int clear(UUID owner) {
		int n = 0;
		for (NemesisRecord r : forOwner(owner)) {
			Boss active = activeBoss(r);
			if (active != null) {
				bosses.bosses().removeMinions(active);
				bosses.bosses().unregister(active);
				active.entity().discard();
			}
			records.remove(r.id);
			n++;
		}
		save();
		return n;
	}

	/** Makes the owner's Nemeses due to return right away (admin/testing). */
	public int summonNow(UUID owner) {
		int n = 0;
		for (NemesisRecord r : forOwner(owner)) {
			if (r.entity == null) {
				r.returnAt = 0;
				n++;
			}
		}
		checkReturns();
		return n;
	}

	/** For testing: makes this boss the player's Nemesis right away, as if it had killed them. */
	public @Nullable NemesisRecord forceCreate(Boss boss, ServerPlayer owner) {
		if (boss.isNemesis()) {
			return of(boss);
		}
		NemesisRecord created = create(boss, owner.getUUID(), owner.getName().getString());
		created.kills = 1;
		updateTitles(created);
		configure(boss, created, true);
		save();
		bosses.presentation().tell(owner, "nemesis-born", boss(displayName(created)));
		return created;
	}

	// =====================================================================
	// Becoming a Nemesis / growing
	// =====================================================================

	/**
	 * A boss killed a player ({@code kills}: players it has killed this outing). Returns true if the Nemesis system
	 * took care of the boss (it is a Nemesis, or just became one), so the two-kills rule is handled here.
	 */
	public boolean onKilledPlayer(Boss boss, ServerPlayer victim, int kills) {
		BossSettings f = f();
		if (!f.nemesisEnabled) {
			return false;
		}
		NemesisRecord r = of(boss);
		if (r != null) {
			boolean owner = r.owner.equals(victim.getUUID());
			if (owner) {
				r.kills++;
				r.ownerDownTick = Ticks.now();
				absorbFight(r, boss, victim.getUUID());
				removeDroppedChallenge(victim, r); // its note comes back fresh on respawn
				r.challengePending = true;
			}
			r.focus = null;
			if (grow(r, f.nemesisLevelsOnKill, boss.entity())) { // any player it kills makes it stronger
				adapt(boss, r);
			}
			refreshBody(boss, r);
			int limit = f.despawnAfterKills;
			if (limit > 0 && kills >= limit) {
				// Two players down: it leaves, and comes back after the full wait.
				r.returnAt = clock() + Math.round(f.nemesisReturnDays * DAY);
				bosses.later(1, () -> {
					if (records.get(r.id) == r && activeBoss(r) == boss && boss.entity().isAlive()) {
						vanish(boss, r);
					}
				});
			}
			save();
			if (owner) {
				bosses.presentation().tell(victim, "nemesis-grows", boss(displayName(r)));
			}
			return true;
		}
		if (forOwner(victim.getUUID()).size() >= f.nemesisMaxPerPlayer || !Rng.chance(f.nemesisKillChance)) {
			return false;
		}
		NemesisRecord created = create(boss, victim.getUUID(), victim.getName().getString());
		created.kills = 1;
		created.challengePending = true; // they wake up with its Challenge
		updateTitles(created);
		configure(boss, created, false);
		retreat(boss, created);
		bosses.presentation().tell(victim, "nemesis-born", boss(displayName(created)));
		return true;
	}

	/**
	 * A player stopped fighting a boss (their engagement expired). Returns true if the boss withdrew (as a new or
	 * existing Nemesis), so the caller stops handling it.
	 */
	public boolean onPlayerFled(Boss boss, UUID playerId) {
		BossSettings f = f();
		ServerPlayer p = online(playerId);
		boolean reallyGone = p == null || p.level() != boss.level() || p.distanceToSqr(boss.entity()) > 24 * 24;
		if (!f.nemesisEnabled || boss.isNemesis() || !reallyGone || (p != null && !p.isAlive())) {
			boss.forgetFight(playerId);
			return false;
		}
		boolean qualifies = boss.rank().atLeast(f.nemesisEscapeMinRank)
			&& boss.damageBy(playerId) >= boss.maxHealth() * f.nemesisEscapeDamagePercent / 100.0
			&& forOwner(playerId).size() < f.nemesisMaxPerPlayer
			&& Rng.chance(f.nemesisEscapeChance);
		if (!qualifies) {
			boss.forgetFight(playerId);
			return false;
		}
		String name = p != null ? p.getName().getString() : nameOf(playerId);
		NemesisRecord created = create(boss, playerId, name);
		created.escapes = 1;
		updateTitles(created);
		configure(boss, created, false);
		retreat(boss, created);
		if (p != null) {
			bosses.presentation().tell(p, "nemesis-born", boss(displayName(created)));
		}
		return true;
	}

	private String nameOf(UUID id) {
		for (NemesisRecord r : records.values()) {
			if (r.owner.equals(id)) {
				return r.ownerName;
			}
		}
		return "?";
	}

	/**
	 * Runs every boss tick for an active Nemesis: keeps it hunting its owner, and withdraws it (one level stronger)
	 * if the owner logs off, changes world, or stays far away. Returns true if it withdrew.
	 */
	public boolean tickActive(Boss boss) {
		NemesisRecord r = of(boss);
		if (r == null) {
			return false;
		}
		if (r.prowlUntil > 0) {
			return prowl(boss, r);
		}
		ServerPlayer owner = online(r.owner);
		LivingEntity e = boss.entity();
		int now = Ticks.now();
		// right after killing its owner it only stays if they come back close; otherwise it would loiter
		double reach = now - r.ownerDownTick < 2400 ? 40 : f().nemesisEscapeDistance;
		boolean inReach = owner != null && owner.isAlive() && owner.level() == e.level()
			&& owner.distanceToSqr(e) <= reach * reach;
		ServerPlayer focus = focus(boss, r, owner);
		if (inReach || focus != null) {
			r.lostTicks = 0;
			ServerPlayer chase = focus != null ? focus : owner;
			if (e instanceof Mob mob && chase != null && boss.fightable(chase) && mob.getTarget() != chase) {
				mob.setTarget(chase);
			}
			return false;
		}
		r.lostTicks += 10;
		if (owner != null && r.lostTicks < 300) {
			return false; // 15 seconds of grace
		}
		if (now - r.ownerDownTick < 2400) {
			// it killed its owner a moment ago and they woke up far away: that isn't fleeing
			return retreat(boss, r);
		}
		r.escapes++;
		absorbFight(r, boss, r.owner);
		if (grow(r, f().nemesisLevelsOnEscape, e)) {
			adapt(boss, r);
		}
		refreshBody(boss, r);
		boolean removed = retreat(boss, r);
		if (owner != null) {
			bosses.presentation().tell(owner, "nemesis-flee", boss(displayName(r)));
		}
		return removed;
	}

	/**
	 * Team fights: it goes after its owner, unless another player hurts it a lot more than whoever it's after
	 * (over the last 10 seconds, by aggro-switch-percent of its health): then it turns on them. Returns the player it
	 * is after if that isn't simply its owner, or null.
	 */
	private @Nullable ServerPlayer focus(Boss boss, NemesisRecord r, @Nullable ServerPlayer owner) {
		LivingEntity e = boss.entity();
		int now = Ticks.now();
		ServerPlayer current = r.focus == null ? owner : online(r.focus);
		if (r.focus != null && current == null) {
			current = owner; // the one it switched to logged off: back to its owner
			r.focus = null;
		}
		if (current != null && (!boss.fightable(current) || current.level() != e.level()
			|| current.distanceToSqr(e) > 40 * 40)) {
			current = owner;
			r.focus = null;
		}
		UUID top = boss.topRecentDamager(now);
		if (top != null && (current == null || !top.equals(current.getUUID()))) {
			double lead = boss.recentDamage(top, now) - (current == null ? 0 : boss.recentDamage(current.getUUID(), now));
			ServerPlayer challenger = online(top);
			if (challenger != null && boss.fightable(challenger)
				&& lead >= boss.maxHealth() * f().nemesisAggroSwitchPercent / 100.0) {
				r.focus = top;
				current = challenger;
				Fx.play(e, "entity.ravager.roar", 0.8f, 1.2f);
			}
		}
		return current != null && owner != null && current.getUUID().equals(owner.getUUID()) ? null : current;
	}

	// =====================================================================
	// Prowling: after it lets its prey go, it roams for a while and feeds on other mobs
	// =====================================================================

	public boolean isProwling(Boss boss) {
		NemesisRecord r = of(boss);
		return r != null && r.prowlUntil > 0;
	}

	/** Mobs driven by "brain" AI (piglins, hoglins, breezes...) can't be steered, so they don't prowl. */
	private static final Set<EntityType<?>> BRAIN_AI = Set.of(EntityTypes.PIGLIN, EntityTypes.PIGLIN_BRUTE,
		EntityTypes.HOGLIN, EntityTypes.ZOGLIN, EntityTypes.BREEZE, EntityTypes.WARDEN, EntityTypes.CREAKING);

	/** Leaves the fight: prowls nearby for a few minutes (if enabled), then vanishes until its return. */
	private boolean retreat(Boss boss, NemesisRecord r) {
		r.lostTicks = 0;
		r.returnAt = clock() + Math.round(f().nemesisReturnDays * DAY);
		LivingEntity e = boss.entity();
		int prowlTicks = (int) Math.round(f().nemesisProwlMinutes * 1200);
		if (prowlTicks <= 0 || !e.isAlive() || BRAIN_AI.contains(e.getType())) {
			vanish(boss, r);
			return true;
		}
		r.prowlUntil = Ticks.now() + prowlTicks;
		r.provoker = null;
		boss.engaged().clear();
		if (e instanceof Mob mob) {
			mob.setTarget(null);
		}
		bosses.bossBars().refreshViewers(boss);
		Fx.particle(boss.level(), ParticleTypes.LARGE_SMOKE, Fx.center(e), 30, 0.6, 0.05);
		Fx.play(e, "entity.wither.ambient", 0.6f, 0.6f);
		save();
		return false;
	}

	/** Runs every boss tick while prowling: hunt nearby mobs, ignore players unless provoked. */
	private boolean prowl(Boss boss, NemesisRecord r) {
		LivingEntity e = boss.entity();
		if (Ticks.now() >= r.prowlUntil) {
			vanish(boss, r);
			return true;
		}
		if (r.provoker != null) {
			ServerPlayer p = online(r.provoker);
			if (p != null && boss.fightable(p) && p.level() == e.level() && p.distanceToSqr(e) < 24 * 24) {
				if (e instanceof Mob mob && mob.getTarget() != p) {
					mob.setTarget(p);
				}
				return false; // busy fighting whoever attacked it
			}
			r.provoker = null;
			boss.engaged().clear();
		}
		if (e instanceof Mob mob && !(e instanceof Creeper) && r.outingKills < f().nemesisKillLevelsPerOuting) {
			LivingEntity target = mob.getTarget();
			if (target == null || !target.isAlive() || target instanceof Player) {
				LivingEntity prey = findPrey(e);
				if (prey != null) {
					mob.setTarget(prey);
				}
			}
		}
		if (Rng.chance(20)) {
			Fx.particle(boss.level(), ParticleTypes.SMOKE, Fx.center(e), 6, 0.4, 0.01);
		}
		return false;
	}

	private static @Nullable LivingEntity findPrey(LivingEntity hunter) {
		AABB box = hunter.getBoundingBox().inflate(16, 8, 16);
		LivingEntity best = null;
		double bestDist = Double.MAX_VALUE;
		for (LivingEntity near : hunter.level().getEntitiesOfClass(LivingEntity.class, box, n -> n != hunter && n.isAlive())) {
			if (near instanceof Player || near instanceof ArmorStand || !(near instanceof Animal || near instanceof Enemy)
				|| BossManager.isBoss(near) || BossManager.isMinion(near) || BossManager.isPet(near)
				|| Data.has(near, BossKeys.STATUE) || (near instanceof TamableAnimal t && t.isTame()) || near.isInvulnerable()
				|| BOSS_MOBS.contains(near.getType())) {
				continue;
			}
			double d = near.distanceToSqr(hunter);
			if (d < bestDist) {
				bestDist = d;
				best = near;
			}
		}
		return best;
	}

	private static final Set<EntityType<?>> BOSS_MOBS = Set.of(EntityTypes.WITHER, EntityTypes.ENDER_DRAGON,
		EntityTypes.WARDEN, EntityTypes.RAVAGER, EntityTypes.ELDER_GUARDIAN);

	/** A player attacked a prowling Nemesis: it turns on them. */
	public void onProvoked(Boss boss, ServerPlayer attacker) {
		NemesisRecord r = of(boss);
		if (r == null || r.prowlUntil <= 0) {
			return;
		}
		if (attacker.getUUID().equals(r.owner)) {
			r.prowlUntil = 0; // its prey came back for more: the hunt is on again
			r.provoker = null;
		} else if (attacker.getUUID().equals(r.provoker)) {
			return;
		} else {
			r.provoker = attacker.getUUID(); // someone else: it fights them off, but keeps prowling
		}
		if (boss.entity() instanceof Mob mob) {
			mob.setTarget(attacker);
		}
		bosses.presentation().tell(attacker, "nemesis-provoked", boss(displayName(r)));
	}

	/** While prowling it only targets players who attacked it. */
	public boolean mayTarget(Boss boss, ServerPlayer player) {
		NemesisRecord r = of(boss);
		return r == null || r.prowlUntil <= 0 || player.getUUID().equals(r.provoker);
	}

	/** Every mob a Nemesis kills makes it one level stronger (a few per outing). */
	public void onPreyKilled(Boss boss, LivingEntity prey) {
		NemesisRecord r = of(boss);
		if (r == null || r.outingKills >= f().nemesisKillLevelsPerOuting || r.level >= f().nemesisMaxLevel) {
			return;
		}
		r.outingKills++;
		grow(r, 1, boss.entity());
		refreshBody(boss, r);
		save();
		LivingEntity e = boss.entity();
		Fx.particle(boss.level(), ParticleTypes.SOUL, Fx.center(e), 12, 0.4, 0.03);
		Fx.play(e, "entity.player.levelup", 0.6f, 0.5f);
	}

	/** Re-applies level scaling, gear and name to the living Nemesis after it levels up (keeps its health %). */
	private void refreshBody(Boss boss, NemesisRecord r) {
		LivingEntity e = boss.entity();
		if (!e.isAlive()) {
			return;
		}
		double ratio = boss.healthRatio();
		applyBody(boss, r);
		e.setHealth((float) Math.max(1, Math.min(boss.maxHealth(), boss.maxHealth() * ratio)));
		applyRuntime(boss, r);
	}

	private NemesisRecord create(Boss boss, UUID owner, String ownerName) {
		String id = UUID.randomUUID().toString().substring(0, 8);
		LivingEntity e = boss.entity();
		NemesisRecord r = new NemesisRecord(id, owner, ownerName, e.getType(), boss.rank());
		r.traits = new ArrayList<>(boss.traitIds());
		r.baseTraitCount = r.traits.size();
		r.createdAt = clock();
		r.wildRank = BossManager.wildRank(e, boss.rank());
		ServerPlayer p = online(owner);
		if (p != null) {
			QuestFlags.mark(p, "nemesis_created");
		}
		r.name = NemesisNames.pick(r.type, takenNames());
		r.surrender = Surrender.pick(r.type);
		Synergies.Synergy synergy = Synergies.find(boss.traits());
		r.epithet = synergy != null ? synergy.title()
			: !boss.traits().isEmpty() ? boss.traits().getFirst().adjective() : f().rank(boss.rank()).name();
		r.baby = e.isBaby();
		r.size = e instanceof AbstractCubeMob cube ? cube.getSize() : -1;
		ItemStack held = e.getMainHandItem();
		r.hand = held.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(held.getItem()).toString();
		r.gearFloor = BossArmor.wornStage(e);
		absorbFight(r, boss, owner);
		records.put(id, r);
		return r;
	}

	/**
	 * The rank of Souls (and salvage) a Nemesis pays: its own, unless it was raised with Souls before it became a
	 * Nemesis; then the rank it was found at, so Souls spent never come back multiplied.
	 */
	public BossRank soulRank(Boss boss) {
		NemesisRecord r = of(boss);
		if (r != null && r.wildRank != null && r.wildRank.ordinal() < r.baseRank.ordinal()) {
			return r.wildRank;
		}
		return boss.rank();
	}

	/** Remembers how the owner fought (melee or ranged) so the Nemesis can adapt. */
	private void absorbFight(NemesisRecord r, Boss boss, UUID owner) {
		int[] style = boss.hitStyle(owner);
		r.meleeHits += style[0];
		r.rangedHits += style[1];
		boss.forgetFight(owner);
	}

	/** Returns true if it actually gained a level (not already at the cap). */
	private boolean grow(NemesisRecord r, int levels, LivingEntity sample) {
		BossSettings f = f();
		int before = r.level;
		r.level = Math.min(f.nemesisMaxLevel, r.level + levels);
		// It climbs a rank every few levels (up to Legendary), but never drops below the rank it started at.
		int climbed = Math.min(BossRank.GOLD.ordinal(), r.baseRank.ordinal() + (r.level - 1) / f.nemesisLevelsPerRank);
		r.rank = BossRank.values()[Math.max(r.baseRank.ordinal(), climbed)];
		int wanted = Math.min(f.nemesisMaxTraits, r.baseTraitCount + (r.level - 1) / f.nemesisLevelsPerTrait);
		int guard = 0;
		while (r.traits.size() < wanted && guard++ < 10) {
			BossTrait next = counterTrait(r, sample);
			if (next == null) {
				next = bosses.traits().rollExtra(sample, resolve(r, sample));
			}
			if (next == null) {
				break;
			}
			r.traits.add(next.id());
		}
		updateTitles(r);
		return r.level > before;
	}

	/**
	 * New traits it picks when it can: runners get chased down (by leaps and charges: bosses don't simply outrun
	 * you), and a killer turns executioner. How you fight is handled by {@link #adapt}.
	 */
	private @Nullable BossTrait counterTrait(NemesisRecord r, LivingEntity sample) {
		List<String> wishes = new ArrayList<>();
		if (r.escapes > r.kills) {
			wishes.addAll(List.of("leaping", "charging"));
		}
		if (r.kills >= 2) {
			wishes.add("executioner");
		}
		List<BossTrait> have = resolve(r, sample);
		for (String id : wishes) {
			BossTrait t = bosses.traits().get(id);
			if (t == null || r.traits.contains(id) || !bosses.traits().isEnabled(t) || !t.supports(sample)) {
				continue;
			}
			boolean ok = true;
			for (BossTrait h : have) {
				if (!bosses.traits().compatible(h, t)) {
					ok = false;
					break;
				}
			}
			if (ok) {
				return t;
			}
		}
		return null;
	}

	private List<BossTrait> resolve(NemesisRecord r, LivingEntity sample) {
		List<BossTrait> out = new ArrayList<>();
		for (String id : r.traits) {
			BossTrait t = bosses.traits().get(id);
			if (t != null && t.supports(sample)) {
				out.add(t);
			}
		}
		return out;
	}

	/** History titles. The newest earned title is the one shown in the name. */
	private void updateTitles(NemesisRecord r) {
		addTitle(r, r.returns >= 1, "Returned");
		addTitle(r, r.escapes >= 2, "Twice-Fled");
		addTitle(r, r.kills >= 2, "Relentless");
		addTitle(r, r.level >= 5, "Unbroken");
		addTitle(r, r.kills >= 5, "Butcher");
		addTitle(r, r.level >= 10, "Dreaded");
		addTitle(r, r.level >= Math.min(15, f().nemesisMaxLevel), "Eternal");
	}

	private static void addTitle(NemesisRecord r, boolean earned, String title) {
		if (earned && !r.titles.contains(title)) {
			r.titles.add(title);
		}
	}

	/**
	 * "☆ Returned Scrawl the Bulwark [Lv 7]": one white star (not rank stars, so it reads as its own category),
	 * its history title, its own name, and what it was known for as a boss.
	 */
	public MutableComponent displayName(NemesisRecord r) {
		return Component.literal("☆ ").withStyle(ChatFormatting.WHITE)
			.append(Component.literal(r.title() + " ").withStyle(ChatFormatting.GRAY))
			.append(Component.literal(r.name).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD))
			.append(Component.literal(" the " + r.epithet).withStyle(ChatFormatting.WHITE))
			.append(Component.literal(" [Lv " + r.level + "]").withStyle(ChatFormatting.DARK_GRAY));
	}

	public String plainName(NemesisRecord r) {
		return r.title() + " " + r.name + " the " + r.epithet;
	}

	/** Removes the Nemesis from the world; it comes back when its return time comes. */
	private void vanish(Boss boss, NemesisRecord r) {
		LivingEntity e = boss.entity();
		r.entity = null;
		r.lostTicks = 0;
		r.prowlUntil = 0;
		r.provoker = null;
		if (r.returnAt <= clock()) {
			r.returnAt = clock() + Math.round(f().nemesisReturnDays * DAY);
		}
		Fx.particle(boss.level(), ParticleTypes.LARGE_SMOKE, Fx.center(e), 40, 0.6, 0.05);
		Fx.play(e, "entity.wither.ambient", 0.6f, 0.6f);
		bosses.bosses().removeMinions(boss);
		bosses.bosses().unregister(boss);
		e.discard();
		save();
	}

	/** The boss's mob unloaded (its chunk, or the server stopping): it slinks off and returns a bit later. */
	public void onUnload(Boss boss) {
		NemesisRecord r = of(boss);
		if (r != null && boss.uuid().equals(r.entity)) {
			r.entity = null;
			r.prowlUntil = 0;
			r.provoker = null;
			r.returnAt = Math.max(r.returnAt, clock() + DAY / 8);
			save();
		}
	}

	// =====================================================================
	// Returning
	// =====================================================================

	/** Every few seconds: brings back Nemeses whose time has come. */
	public void checkReturns() {
		long now = clock(); // also keeps the day counter in step with sleeping
		if (!f().nemesisEnabled || records.isEmpty()) {
			return;
		}
		long maxWait = Math.round(f().nemesisReturnDays * DAY);
		boolean dirty = false;
		for (NemesisRecord r : List.copyOf(records.values())) {
			if (r.entity != null && activeBoss(r) == null) {
				// Removed some other way: it slinks off and returns a bit later.
				r.entity = null;
				r.prowlUntil = 0;
				r.returnAt = Math.max(r.returnAt, now + DAY / 8);
				dirty = true;
			}
			if (r.returnAt - now > maxWait) {
				r.returnAt = now + maxWait; // the clock was turned back
				dirty = true;
			}
			if (r.entity != null || now < r.returnAt) {
				continue;
			}
			ServerPlayer owner = online(r.owner);
			if (owner == null || !canHuntHere(owner)) {
				continue;
			}
			spawnFor(r, owner, 14, 22, false);
		}
		if (dirty) {
			save();
		}
	}

	private boolean canHuntHere(ServerPlayer owner) {
		GameType mode = owner.gameMode();
		ServerLevel level = owner.level();
		return owner.isAlive() && (mode == GameType.SURVIVAL || mode == GameType.ADVENTURE)
			&& level.dimension() != Level.END && level.getDifficulty() != Difficulty.PEACEFUL && f().worldAllowed(level);
	}

	/** Brings the Nemesis to its owner, between {@code min} and {@code max} blocks away. False if it couldn't. */
	private boolean spawnFor(NemesisRecord r, ServerPlayer owner, int min, int max, boolean challenged) {
		ServerLevel level = owner.level();
		Vec3 spot = SafeSpots.around(level, owner.position(), min, max, 16);
		if (spot == null) {
			return false; // try again on the next check
		}
		List<BossTrait> traits = new ArrayList<>();
		for (String id : r.traits) {
			BossTrait t = bosses.traits().get(id);
			if (t != null && bosses.traits().isEnabled(t)) {
				traits.add(t);
			}
		}
		// turned-off traits leave its memory, so its next level-ups can learn something else
		r.traits.removeIf(id -> bosses.traits().get(id) == null || !bosses.traits().isEnabled(bosses.traits().get(id)));
		r.baseTraitCount = Math.min(r.baseTraitCount, r.traits.size());
		Boss boss = bosses.bosses().summon(r.type, level, spot, r.rank, traits, false, "nemesis");
		if (boss == null) {
			Entity probe = r.type.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			boolean mob = probe instanceof Mob;
			if (probe != null) {
				probe.discard();
			}
			if (!mob) {
				records.remove(r.id); // that mob can't exist any more
				save();
			}
			return false; // the spawn was blocked here; try again on the next check
		}
		if (challenged) {
			BossManager.markCreated(boss, "challenge"); // its Souls, but no Totem
		}
		r.recentDamage.clear();
		r.focus = null;
		r.returns++;
		r.outingKills = 0;
		r.prowlUntil = 0;
		updateTitles(r);
		configure(boss, r, true);
		bosses.bossBars().updateHealth(boss);
		save();

		Component name = displayName(r);
		Fx.title(owner, messages().get("nemesis-return-title", Map.of()), messages().get("nemesis-return-subtitle", boss(name)),
			8, 70, 20);
		if (f().bossChat) {
			owner.sendSystemMessage(messages().prefixed("nemesis-return-subtitle", boss(name)));
		}
		Fx.playTo(owner, "entity.wither.spawn", 0.7f, 0.8f);
		bosses.bosses().engage(boss, owner);
		return true;
	}

	/** Applies everything a Nemesis is: its body (same as last time), level scaling, gear, name and persistence. */
	private void configure(Boss boss, NemesisRecord r, boolean fullHealth) {
		LivingEntity e = boss.entity();
		double ratio = boss.healthRatio();
		Data.of(e).putString(BossKeys.NEMESIS, r.id);
		if (e instanceof Mob mob) {
			mob.setBaby(r.baby);
		}
		if (e instanceof AbstractCubeMob cube && r.size > 0) {
			cube.setSize(r.size, false);
		}
		if (e instanceof AbstractPiglin piglin) {
			piglin.setImmuneToZombification(true); // it would turn into an ordinary mob in the Overworld
		}
		if (e instanceof Hoglin hoglin) {
			hoglin.setImmuneToZombification(true);
		}
		applyBody(boss, r);
		// A returning Nemesis arrives at full strength; a boss that just became one keeps its wounds.
		e.setHealth((float) Math.max(1, fullHealth ? boss.maxHealth() : Math.min(boss.maxHealth(), boss.maxHealth() * ratio)));
		applyRuntime(boss, r);
	}

	/** Level-based health, size and gear. */
	private void applyBody(Boss boss, NemesisRecord r) {
		BossSettings f = f();
		LivingEntity e = boss.entity();
		int level = r.level;
		Attrs.set(e, Attributes.MAX_HEALTH, BossKeys.MOD_NEMESIS_HEALTH, level * f.nemesisHealthPerLevel / 100.0,
			AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
		Attrs.set(e, Attributes.SCALE, BossKeys.MOD_NEMESIS_SIZE, Math.min(f.nemesisMaxExtraSize, level * f.nemesisSizePerLevel),
			AttributeModifier.Operation.ADD_VALUE);
		if (f.nemesisGearEvolution) {
			// Gear upgrades with level, starting from the tier that fits its rank (a Legendary starts in netherite).
			BossArmor.applyNemesis(f, e, level, r.id, r.rank, handName(r.hand), r.gearFloor);
		} else if (!r.hand.isEmpty() && e instanceof Mob mob) {
			Identifier id = Identifier.tryParse(r.hand);
			Item held = id == null ? null : BuiltInRegistries.ITEM.getOptional(id).orElse(null);
			if (held != null) {
				mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(held));
				mob.setDropChance(EquipmentSlot.MAINHAND, 0f);
			}
		}
	}

	/** "minecraft:crossbow" -> "CROSSBOW" (what the gear code checks). */
	private static String handName(String hand) {
		int colon = hand.indexOf(':');
		return (colon >= 0 ? hand.substring(colon + 1) : hand).toUpperCase(Locale.ROOT);
	}

	/** The in-memory side of a Nemesis (damage, trait power, name, bar). Its body is saved on the mob itself. */
	private void applyRuntime(Boss boss, NemesisRecord r) {
		BossSettings f = f();
		LivingEntity e = boss.entity();
		r.entity = e.getUUID();
		r.lostTicks = 0;
		boss.setNemesisId(r.id);
		Data.flag(e, Spawns.TRANSIENT, true); // never saved into the world; the record brings it back
		if (e instanceof Mob mob) {
			mob.setPersistenceRequired(); // never despawns while out
		}
		boss.setNemesisScaling(1.0 + r.level * f.nemesisDamagePerLevel / 100.0,
			1.0 + r.level * f.nemesisPowerPerLevel / 100.0, f.nemesisMaxDamageMultiplier);
		boss.rename(displayName(r), plainName(r));
		e.setCustomNameVisible(f.alwaysShowName);
		ServerBossEvent bar = boss.bar();
		if (bar != null) {
			bar.setColor(BossEvent.BossBarColor.PINK);
			bar.setOverlay(BossEvent.BossBarOverlay.NOTCHED_20);
		}
		bosses.bossBars().retitle(boss);
	}

	/** True if a Nemesis mob that just appeared (e.g. through a portal) is the real one and should stay. */
	public boolean canReattach(String id, LivingEntity entity) {
		NemesisRecord r = records.get(id);
		if (r == null || !f().nemesisEnabled) {
			return false;
		}
		Boss other = activeBoss(r);
		return other == null || other.uuid().equals(entity.getUUID());
	}

	public void reattach(Boss boss, String id) {
		NemesisRecord r = records.get(id);
		if (r != null) {
			applyRuntime(boss, r);
		}
	}

	private @Nullable Boss activeBoss(NemesisRecord r) {
		return r.entity == null ? null : bosses.bosses().get(r.entity);
	}

	// =====================================================================
	// Adaptation: it learns how you fight it
	// =====================================================================

	/** Damage it took, sorted by kind, so it can adapt at its next level-up. */
	public void recordDamage(Boss boss, DamageSource source, @Nullable LivingEntity attacker, double damage) {
		NemesisRecord r = of(boss);
		DamageKind kind = DamageKind.of(source, attacker);
		if (r == null || kind == null || damage <= 0 || !DamageKind.learnsFrom(attacker)) {
			return;
		}
		r.recentDamage.merge(kind, damage, Double::sum);
	}

	/**
	 * After a level-up from a fight: it adapts to the kind of damage that hurt it most since it last adapted. The
	 * first time it resists that kind; if you keep at it, it becomes immune (at most max-immunities; fire is free).
	 * Pets: it resists them, and hits them harder, but is never fully immune.
	 */
	private void adapt(Boss boss, NemesisRecord r) {
		BossSettings f = f();
		if (!f.nemesisAdaptation || r.recentDamage.isEmpty()) {
			return;
		}
		List<Map.Entry<DamageKind, Double>> order = new ArrayList<>(r.recentDamage.entrySet());
		order.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
		double meaningful = Math.max(4, boss.maxHealth() * 0.02); // a stray scratch doesn't teach it anything
		for (Map.Entry<DamageKind, Double> entry : order) {
			if (entry.getValue() < meaningful) {
				break;
			}
			DamageKind kind = entry.getKey();
			int now = r.adapted.getOrDefault(kind, 0);
			int next = now + 1;
			if (next > 2 || (kind == DamageKind.PET && next > 1)
				|| (next == 2 && kind != DamageKind.FIRE && r.immunities() >= f.nemesisMaxImmunities)) {
				continue; // can't learn more against this one: try the next
			}
			r.adapted.put(kind, next);
			r.recentDamage.clear();
			adaptedFx(boss, next == 2);
			// Quest Book: its owner and whoever is fighting it saw it change
			ServerPlayer owner = online(r.owner);
			if (owner != null) {
				QuestFlags.mark(owner, "nemesis_adapted");
			}
			for (UUID fighter : boss.engaged().keySet()) {
				ServerPlayer p = online(fighter);
				if (p != null && !fighter.equals(r.owner)) {
					QuestFlags.mark(p, "nemesis_adapted");
				}
			}
			return;
		}
	}

	/** A smoky pulse when it adapts, so sharp-eyed players notice that something changed. */
	private static void adaptedFx(Boss boss, boolean immune) {
		LivingEntity e = boss.entity();
		Vec3 at = Fx.center(e);
		Fx.particle(boss.level(), ParticleTypes.LARGE_SMOKE, at, immune ? 60 : 35, 0.9, 0.04);
		Fx.particle(boss.level(), immune ? ParticleTypes.SCULK_SOUL : ParticleTypes.SMOKE, at, 30, 1.2, 0.03);
		Fx.play(e, immune ? "block.respawn_anchor.set_spawn" : "block.respawn_anchor.charge", 1.0f, 0.6f);
	}

	/** What it has adapted to hurts it less, or not at all. */
	public void onHurt(Boss boss, DamageContext ctx) {
		NemesisRecord r = of(boss);
		if (r == null || r.adapted.isEmpty() || !f().nemesisAdaptation) {
			return;
		}
		DamageKind kind = DamageKind.of(ctx.source(), ctx.attacker());
		int level = kind == null ? 0 : r.adapted.getOrDefault(kind, 0);
		if (level <= 0) {
			return;
		}
		LivingEntity e = boss.entity();
		if (kind == DamageKind.PET) {
			ctx.multiply(f().nemesisPetDamageTakenPercent / 100.0);
		} else if (level >= 2) {
			ctx.cancel(); // immune: the blow just slides off
			if (kind == DamageKind.FIRE) {
				e.clearFire();
			}
			if (Rng.chance(50)) {
				Fx.particle(boss.level(), ParticleTypes.SMOKE, Fx.center(e), 12, 0.5, 0.02);
				Fx.play(e, "item.shield.block", 0.6f, 0.6f);
			}
			return;
		} else {
			ctx.multiply(f().nemesisResistedPercent / 100.0);
		}
		if (Rng.chance(25)) {
			Fx.particle(boss.level(), ParticleTypes.SMOKE, Fx.center(e), 6, 0.4, 0.02);
		}
	}

	/** Having adapted to pets, it hits them harder too. */
	public void onHitPet(Boss boss, DamageContext ctx) {
		NemesisRecord r = of(boss);
		if (r != null && f().nemesisAdaptation && r.adapted.getOrDefault(DamageKind.PET, 0) > 0) {
			ctx.multiply(1 + f().nemesisPetDamageBonus / 100.0);
		}
	}

	// =====================================================================
	// Its Challenge: call it back now, or surrender
	// =====================================================================

	public void markBossHit(ServerPlayer p) {
		lastBossHit.put(p.getUUID(), Ticks.now());
	}

	/** On respawn: hands over the Challenges it has earned. */
	public void giveChallenges(ServerPlayer player) {
		boolean changed = false;
		for (NemesisRecord r : List.copyOf(records.values())) {
			if (!r.challengePending || !r.owner.equals(player.getUUID())) {
				continue;
			}
			r.challengePending = false;
			changed = true;
			if (carriesChallenge(player, r)) {
				continue; // they still have the current note
			}
			// a new copy: any older copy (left in a chest, dropped where it died) stops working
			r.challengeSerial++;
			give(player, createChallenge(r));
			bosses.presentation().tell(player, "nemesis-challenge-received", boss(displayName(r)));
			QuestFlags.mark(player, "challenge_received");
		}
		if (changed) {
			save();
		}
	}

	/** (Admin) A fresh copy of each of the player's Challenges. Returns how many. */
	public int reissueChallenges(ServerPlayer player) {
		int n = 0;
		for (NemesisRecord r : forOwner(player.getUUID())) {
			r.challengeSerial++; // the new copy replaces any older one
			give(player, createChallenge(r));
			n++;
		}
		if (n > 0) {
			save();
		}
		return n;
	}

	private static void give(ServerPlayer player, ItemStack item) {
		if (!player.getInventory().add(item)) {
			player.spawnAtLocation(player.level(), item);
		}
	}

	/** Whether they carry the current copy of this Nemesis's note (inventory or ender chest). */
	private static boolean carriesChallenge(ServerPlayer p, NemesisRecord r) {
		for (Container container : List.<Container>of(p.getInventory(), p.getEnderChestInventory())) {
			for (int i = 0; i < container.getContainerSize(); i++) {
				ItemStack item = container.getItem(i);
				if (r.id.equals(challengeId(item)) && challengeSerial(item) == r.challengeSerial) {
					return true;
				}
			}
		}
		return false;
	}

	public static @Nullable String challengeId(@Nullable ItemStack item) {
		if (BossItems.kind(item) != BossItems.Kind.CHALLENGE) {
			return null;
		}
		String id = BossItems.data(item).getStringOr("nemesis", "");
		return id.isEmpty() ? null : id;
	}

	private static int challengeSerial(@Nullable ItemStack item) {
		return item == null || item.isEmpty() ? -1 : BossItems.data(item).getIntOr("serial", 0);
	}

	/** The note belongs to a Nemesis that still exists, and it's the current copy. */
	private @Nullable NemesisRecord liveChallenge(@Nullable ItemStack item) {
		String id = challengeId(item);
		NemesisRecord r = id == null ? null : records.get(id);
		return r != null && challengeSerial(item) == r.challengeSerial ? r : null;
	}

	/** The note: its name, how to call it, and its Terms of Surrender. */
	public ItemStack createChallenge(NemesisRecord r) {
		ItemStack item = new ItemStack(ModItems.NEMESIS_CHALLENGE);
		item.set(DataComponents.ITEM_NAME, Component.literal("☠ Challenge of " + r.name)
			.withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		List<Component> lore = new ArrayList<>();
		lore.add(Text.line(r.name + " the " + r.epithet, ChatFormatting.GRAY));
		lore.add(Text.line("Right-click: call it to you now,", ChatFormatting.GRAY));
		lore.add(Text.line("instead of waiting for it.", ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(Text.line("Terms of Surrender:", ChatFormatting.DARK_RED));
		for (String part : Text.wrap("\"" + r.surrender.words() + "\"", 34)) {
			lore.add(Text.noItalic(Component.literal(part).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC)));
		}
		lore.add(Text.line("(" + r.surrender.plain() + " while carrying", ChatFormatting.DARK_GRAY));
		lore.add(Text.line("this note, and the feud ends. No reward.)", ChatFormatting.DARK_GRAY));
		item.set(DataComponents.LORE, new ItemLore(lore));
		BossItems.edit(item, tag -> {
			tag.putString("kind", "challenge");
			tag.putString("nemesis", r.id);
			tag.putInt("serial", r.challengeSerial);
		});
		return item;
	}

	/** Right-click with a Challenge: the Nemesis comes now (20-30 blocks away). The note is used up only if it came. */
	public void useChallenge(ServerPlayer player, ItemStack item, InteractionHand hand) {
		String id = challengeId(item);
		NemesisRecord r = liveChallenge(item);
		if (r == null) {
			player.setItemInHand(hand, ItemStack.EMPTY);
			if (id != null && records.containsKey(id)) {
				// the feud goes on, but a newer copy of the note replaced this one
				Fx.actionBar(player, Component.literal("The ink has faded: a newer copy of this Challenge replaced it.")
					.withStyle(ChatFormatting.GRAY));
			} else {
				bosses.presentation().tell(player, "nemesis-challenge-gone");
			}
			return;
		}
		if (!f().nemesisEnabled) {
			bosses.presentation().tell(player, "nemesis-challenge-blocked");
			return;
		}
		if (!r.owner.equals(player.getUUID())) {
			bosses.presentation().tell(player, "nemesis-challenge-not-yours");
			return;
		}
		if (activeBoss(r) != null) {
			bosses.presentation().tell(player, "nemesis-challenge-out", boss(displayName(r)));
			return;
		}
		if (!canHuntHere(player) || !spawnFor(r, player, 20, 30, true)) {
			bosses.presentation().tell(player, "nemesis-challenge-blocked");
			return;
		}
		ItemStack held = player.getItemInHand(hand);
		if (id.equals(challengeId(held))) {
			held.shrink(1);
		}
	}

	/** It killed its owner: the note they dropped is gone (a fresh copy comes when they respawn). */
	private static void removeDroppedChallenge(ServerPlayer victim, NemesisRecord r) {
		AABB box = victim.getBoundingBox().inflate(3);
		for (ItemEntity drop : victim.level().getEntitiesOfClass(ItemEntity.class, box, d -> d.getAge() < 5)) {
			if (r.id.equals(challengeId(drop.getItem()))) {
				drop.discard();
			}
		}
	}

	/**
	 * A player died: by its Terms of Surrender while carrying its Challenge, the feud is over for good. No reward; the
	 * note is gone. Works whether or not the Nemesis is out.
	 */
	public void checkSurrender(ServerPlayer p, DamageSource source) {
		Integer hit = lastBossHit.remove(p.getUUID());
		if (hit != null && Ticks.now() - hit < 200) {
			return; // a boss had a hand in this (knocked off a ledge, set on fire...)
		}
		for (Boss b : bosses.bosses().active()) {
			if (b.engaged().containsKey(p.getUUID())) {
				return; // mid-fight with a boss (blinded, pulled, knocked about): not a surrender
			}
		}
		// the note was dropped where they died (or is still in the inventory with keepInventory on)
		List<ItemStack> carried = new ArrayList<>();
		List<ItemEntity> dropped = p.level().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3),
			d -> d.getAge() < 5 && challengeId(d.getItem()) != null);
		for (ItemEntity d : dropped) {
			carried.add(d.getItem());
		}
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			carried.add(p.getInventory().getItem(i));
		}
		Set<String> ended = new HashSet<>();
		for (ItemStack item : carried) {
			NemesisRecord r = liveChallenge(item);
			if (r != null && !ended.contains(r.id) && r.owner.equals(p.getUUID()) && r.surrender.matches(source, r.type)) {
				ended.add(r.id);
				Boss active = activeBoss(r);
				if (active != null) {
					LivingEntity e = active.entity();
					Fx.particle(active.level(), ParticleTypes.LARGE_SMOKE, Fx.center(e), 40, 0.6, 0.05);
					bosses.bosses().removeMinions(active);
					bosses.bosses().unregister(active);
					e.discard();
				}
				records.remove(r.id);
				bosses.presentation().tell(p, "nemesis-surrendered", boss(displayName(r)));
			}
		}
		if (ended.isEmpty()) {
			return;
		}
		save();
		for (ItemEntity d : dropped) {
			if (ended.contains(challengeId(d.getItem()))) {
				d.discard();
			}
		}
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (ended.contains(challengeId(p.getInventory().getItem(i)))) {
				p.getInventory().setItem(i, ItemStack.EMPTY); // (with keepInventory on)
			}
		}
	}

	// =====================================================================
	// Death
	// =====================================================================

	/**
	 * Nemesis loot: guaranteed boss gear above the normal ceiling, a guaranteed rune, boosted relic odds, a statue.
	 * Returns the experience multiplier.
	 */
	public double onSlain(Boss boss, @Nullable ServerPlayer killer, List<ItemStack> rewards) {
		NemesisRecord r = of(boss);
		if (r == null) {
			return 1.0;
		}
		if (killer == null) {
			// Killed by something other than a player (a golem, lava...): that isn't the end of a Nemesis.
			r.entity = null;
			r.prowlUntil = 0;
			r.provoker = null;
			r.returnAt = Math.max(r.returnAt, clock() + Math.round(f().nemesisReturnDays * DAY));
			save();
			return 1.0;
		}
		records.remove(r.id);
		save();
		boolean owner = killer.getUUID().equals(r.owner);
		if (owner) {
			QuestFlags.mark(killer, "nemesis_slain"); // Quest Book: Revenge
		}
		RankSettings rs = f().rank(r.rank);
		double xp = 1.0 + r.level * 0.2;
		rewards.add(bosses.rewards().gear().createBonus(r.rank, r.type, plainName(r), 1 + r.level / 10));
		ItemStack rune = BossItems.randomRune(r.rank);
		if (rune != null) {
			rewards.add(rune);
		}
		if (Rng.chance(Math.min(100, rs.rewards().relic() + 3.0 * r.level))) {
			ItemStack relic = BossItems.randomRelic();
			if (relic != null) {
				rewards.add(relic);
			}
		}
		if (f().catalystEnabled && Rng.chance(Math.min(50, rs.rewards().relicCatalyst() + r.level))) {
			rewards.add(BossItems.catalyst());
		}
		ItemStack statue = bosses.statues().create(boss, r, displayName(r));
		if (statue != null) {
			rewards.add(statue);
		}
		// Revenge on the one that hunted you (if it didn't already count, killing you this very outing)
		if (owner && r.kills > 0 && !boss.killed(killer.getUUID())) {
			xp *= f().nemesisRevengeXpMultiplier;
			rewards.addAll(bosses.rewards().roll(boss, killer));
			bosses.presentation().tell(killer, "revenge");
		}
		bosses.presentation().broadcast(boss.level(), messages().prefixed("nemesis-slain",
			Map.of("player", killer.getDisplayName(), "boss", displayName(r))));
		return xp;
	}

	// =====================================================================

	/** Ambient "battle scars": smoke that thickens with the Nemesis's history. */
	public int scarLevel(Boss boss) {
		NemesisRecord r = of(boss);
		return r == null ? 0 : Math.min(6, 1 + r.escapes + r.kills);
	}

	public String describeReturn(NemesisRecord r) {
		if (r.entity != null) {
			return "hunting you now";
		}
		long left = r.returnAt - clock();
		if (left <= 0) {
			return "returning any moment";
		}
		return "returns in " + Text.num(left / (double) DAY) + " days";
	}
}
