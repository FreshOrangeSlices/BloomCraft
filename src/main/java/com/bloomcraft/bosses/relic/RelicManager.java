package com.bloomcraft.bosses.relic;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.combat.DamageContext;
import com.bloomcraft.bosses.item.BossItems;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.Scheduler;
import com.bloomcraft.core.Ticks;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The relic and curse registry, each player's active relics, and the hooks that run them. */
public final class RelicManager {

	public record Active(RelicEffect effect, RelicContext.Slot slot) {
	}

	private final Bosses bosses;
	private final Map<String, RelicEffect> registry = new LinkedHashMap<>();
	private final Set<String> enabled = new HashSet<>();
	private final Map<String, Double> weights = new HashMap<>();

	private final Map<UUID, List<Active>> cache = new HashMap<>();
	private final Set<UUID> passivePlayers = new HashSet<>();
	private final Map<UUID, Map<String, Integer>> cooldowns = new HashMap<>();
	/** Curses put on a player for testing (/bosses curse): curse id -> tick it wears off. */
	private final Map<UUID, Map<String, Integer>> tests = new HashMap<>();
	/** Repeating test triggers: player -> tick they stop. */
	private final Map<UUID, Integer> repeatUntil = new HashMap<>();

	public RelicManager(Bosses bosses) {
		this.bosses = bosses;
		RelicRegistry.registerDefaults(this);
	}

	public void register(RelicEffect effect) {
		registry.put(effect.id(), effect);
	}

	public void load(Cfg root) {
		enabled.clear();
		weights.clear();
		for (RelicEffect effect : registry.values()) {
			Cfg s = root.sectionOrEmpty((effect.curse() ? "curses." : "effects.") + effect.id());
			if (s.getBool("enabled", true)) {
				enabled.add(effect.id());
			}
			weights.put(effect.id(), Math.max(0, s.getDouble("weight", effect.defaultWeight())));
			effect.load(s);
		}
		for (String group : List.of("effects", "curses")) {
			for (String key : root.sectionOrEmpty(group).keys()) {
				if (!registry.containsKey(key)) {
					BloomCraft.LOG.warn("relics.{}.{} does not match any relic (ignored)", group, key);
				}
			}
		}
		MinecraftServer server = Ticks.server();
		if (server != null && server.getPlayerList() != null) {
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				deactivateMissing(p, cache.get(p.getUUID()), List.of());
			}
			cache.clear();
			for (ServerPlayer p : server.getPlayerList().getPlayers()) {
				refresh(p);
			}
		} else {
			cache.clear();
		}
	}

	// =====================================================================
	// Registry
	// =====================================================================

	public @Nullable RelicEffect get(@Nullable String id) {
		return id == null ? null : registry.get(id.toLowerCase(Locale.ROOT));
	}

	public Collection<RelicEffect> all() {
		return registry.values();
	}

	public boolean isEnabled(RelicEffect effect) {
		return enabled.contains(effect.id());
	}

	public List<RelicEffect> enabledEffects(boolean curses) {
		List<RelicEffect> out = new ArrayList<>();
		for (RelicEffect e : registry.values()) {
			if (e.curse() == curses && enabled.contains(e.id())) {
				out.add(e);
			}
		}
		return out;
	}

	public @Nullable RelicEffect rollEffect() {
		return Rng.weighted(enabledEffects(false), e -> weights.getOrDefault(e.id(), 0.0));
	}

	public @Nullable RelicEffect rollCurse() {
		return Rng.weighted(enabledEffects(true), e -> weights.getOrDefault(e.id(), 0.0));
	}

	public double weight(RelicEffect effect) {
		return weights.getOrDefault(effect.id(), 0.0);
	}

	// =====================================================================
	// Active relics
	// =====================================================================

	public List<Active> active(ServerPlayer player) {
		List<Active> list = cache.get(player.getUUID());
		return list != null ? list : refresh(player);
	}

	/** Re-reads the player's equipment. Called when it changes. */
	public List<Active> refresh(ServerPlayer player) {
		Map<String, Active> found = new LinkedHashMap<>();
		bindWornCursedArmor(player);
		collect(player.getMainHandItem(), RelicContext.Slot.HAND, found);
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET}) {
			collect(player.getItemBySlot(slot), RelicContext.Slot.ARMOR, found);
		}
		ItemStack off = player.getOffhandItem();
		if (off.is(Items.SHIELD)) {
			collect(off, RelicContext.Slot.ARMOR, found);
		}
		Map<String, Integer> testing = tests.get(player.getUUID());
		if (testing != null) {
			int now = Ticks.now();
			for (Map.Entry<String, Integer> test : testing.entrySet()) {
				RelicEffect effect = get(test.getKey());
				if (effect != null && test.getValue() > now) {
					found.putIfAbsent(effect.id() + "@test", new Active(effect, RelicContext.Slot.ARMOR));
				}
			}
		}
		List<Active> list = List.copyOf(found.values());
		List<Active> previous = cache.put(player.getUUID(), list);
		deactivateMissing(player, previous, list);
		applyAttributes(player, list);
		boolean hasPassive = list.stream().anyMatch(a -> a.effect().passive());
		if (hasPassive) {
			passivePlayers.add(player.getUUID());
		} else {
			passivePlayers.remove(player.getUUID());
		}
		return list;
	}

	/** Cursed armor gets Curse of Binding as soon as it's worn. */
	private void bindWornCursedArmor(ServerPlayer player) {
		for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET}) {
			ItemStack worn = player.getItemBySlot(slot);
			if (!worn.isEmpty()) {
				BossItems.bindCursedArmor(player.level(), worn);
			}
		}
	}

	private void collect(ItemStack item, RelicContext.Slot slot, Map<String, Active> out) {
		if (item.isEmpty()) {
			return;
		}
		List<String> ids = new ArrayList<>(BossItems.relics(item));
		ids.addAll(BossItems.curses(item));
		for (String id : ids) {
			RelicEffect effect = get(id);
			if (effect != null && enabled.contains(effect.id())) {
				// The same relic on two worn pieces only counts once.
				out.putIfAbsent(effect.id() + "@" + slot, new Active(effect, slot));
			}
		}
	}

	private void deactivateMissing(ServerPlayer player, @Nullable List<Active> before, List<Active> after) {
		if (before == null) {
			return;
		}
		Set<String> still = new HashSet<>();
		for (Active a : after) {
			still.add(a.effect().id());
		}
		Set<String> done = new HashSet<>();
		for (Active a : before) {
			if (!still.contains(a.effect().id()) && done.add(a.effect().id())) {
				a.effect().onDeactivate(player);
			}
		}
	}

	/** Relic stat changes are transient (never saved, so they can't get stuck); rebuilt on every equipment change. */
	private void applyAttributes(ServerPlayer player, List<Active> active) {
		for (RelicEffect effect : registry.values()) {
			List<RelicEffect.AttributeBonus> bonuses = effect.attributeBonuses();
			for (int i = 0; i < bonuses.size(); i++) {
				AttributeInstance inst = player.getAttribute(bonuses.get(i).attribute());
				if (inst != null) {
					inst.removeModifier(attributeKey(effect, i));
				}
			}
		}
		Set<String> applied = new HashSet<>();
		for (Active a : active) {
			RelicEffect effect = a.effect();
			if (!applied.add(effect.id())) {
				continue;
			}
			List<RelicEffect.AttributeBonus> bonuses = effect.attributeBonuses();
			for (int i = 0; i < bonuses.size(); i++) {
				RelicEffect.AttributeBonus b = bonuses.get(i);
				AttributeInstance inst = player.getAttribute(b.attribute());
				if (inst != null) {
					inst.addTransientModifier(new AttributeModifier(attributeKey(effect, i), b.amount(), b.operation()));
				}
			}
		}
		if (player.getHealth() > player.getMaxHealth()) {
			player.setHealth(player.getMaxHealth());
		}
	}

	private static Identifier attributeKey(RelicEffect effect, int index) {
		return BloomCraft.id("relic_" + effect.id().replace('-', '_') + "_" + index);
	}

	// =====================================================================
	// Testing (/bosses curse)
	// =====================================================================

	/** The player has this curse (or relic) for a while, as if they were wearing it. */
	public void addTest(ServerPlayer player, RelicEffect effect, int seconds) {
		UUID id = player.getUUID();
		int until = Ticks.now() + seconds * 20;
		tests.computeIfAbsent(id, k -> new HashMap<>()).put(effect.id(), until);
		refresh(player);
		Scheduler.later(seconds * 20, () -> {
			Map<String, Integer> map = tests.get(id);
			if (map == null || !Integer.valueOf(until).equals(map.get(effect.id()))) {
				return;
			}
			map.remove(effect.id());
			if (map.isEmpty()) {
				tests.remove(id);
			}
			ServerPlayer p = player.level().getServer().getPlayerList().getPlayer(id);
			if (p != null) {
				refresh(p);
			}
		});
	}

	/** Fires the effect's event every {@code everyTicks} for {@code seconds}, whatever its normal odds. */
	public void repeatTest(ServerPlayer player, RelicEffect effect, int seconds, int everyTicks) {
		UUID id = player.getUUID();
		int stopAt = Ticks.now() + seconds * 20;
		repeatUntil.put(id, stopAt);
		MinecraftServer server = player.level().getServer();
		Scheduler.repeat(1, everyTicks, () -> {
			Integer until = repeatUntil.get(id);
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null || until == null || until != stopAt || Ticks.now() > stopAt) {
				return false;
			}
			if (p.isAlive()) {
				effect.trigger(p, this);
			}
			return true;
		});
	}

	public static boolean hasTrigger(RelicEffect effect) {
		try {
			return effect.getClass().getMethod("trigger", ServerPlayer.class, RelicManager.class).getDeclaringClass()
				!= RelicEffect.class;
		} catch (NoSuchMethodException ex) {
			return false;
		}
	}

	/** Ends every test on the player. Returns how many curses were being tested. */
	public int clearTests(ServerPlayer player) {
		repeatUntil.remove(player.getUUID());
		Map<String, Integer> map = tests.remove(player.getUUID());
		refresh(player);
		return map == null ? 0 : map.size();
	}

	public void forget(ServerPlayer player) {
		repeatUntil.remove(player.getUUID());
		tests.remove(player.getUUID());
		deactivateMissing(player, cache.get(player.getUUID()), List.of());
		applyAttributes(player, List.of());
		cache.remove(player.getUUID());
		passivePlayers.remove(player.getUUID());
		cooldowns.remove(player.getUUID());
	}

	/** Once a second. */
	public void tickPassives(MinecraftServer server) {
		for (UUID id : List.copyOf(passivePlayers)) {
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null) {
				passivePlayers.remove(id);
				continue;
			}
			if (!p.isAlive()) {
				continue;
			}
			Set<String> ran = new HashSet<>();
			for (Active a : active(p)) {
				if (a.effect().passive() && ran.add(a.effect().id())) {
					a.effect().onPassive(context(p, a));
				}
			}
		}
	}

	public void shutdown(MinecraftServer server) {
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			forget(p);
		}
		cache.clear();
		passivePlayers.clear();
		cooldowns.clear();
	}

	/** Per-player cooldown helper. True (and starts the cooldown) if ready. */
	public boolean ready(ServerPlayer player, String key, int cooldownTicks) {
		int now = Ticks.now();
		Map<String, Integer> map = cooldowns.computeIfAbsent(player.getUUID(), k -> new HashMap<>());
		Integer until = map.get(key);
		if (until != null && now < until) {
			return false;
		}
		map.put(key, now + cooldownTicks);
		return true;
	}

	private RelicContext context(ServerPlayer p, Active a) {
		return new RelicContext(p, a.slot(), this);
	}

	public Bosses bosses() {
		return bosses;
	}

	// =====================================================================
	// Hooks
	// =====================================================================

	public void onAttack(ServerPlayer p, LivingEntity victim, DamageContext damage, boolean victimIsBoss) {
		for (Active a : active(p)) {
			a.effect().onAttack(context(p, a), victim, damage, victimIsBoss);
		}
	}

	public void afterAttack(ServerPlayer p, LivingEntity victim, double finalDamage, DamageContext damage) {
		for (Active a : active(p)) {
			a.effect().afterAttack(context(p, a), victim, finalDamage, damage);
		}
	}

	public void onDamaged(ServerPlayer p, DamageContext damage) {
		for (Active a : active(p)) {
			a.effect().onDamaged(context(p, a), damage);
		}
	}

	public void afterDamaged(ServerPlayer p, double finalDamage, DamageContext damage) {
		for (Active a : active(p)) {
			a.effect().afterDamaged(context(p, a), finalDamage, damage);
		}
	}

	public void onKill(ServerPlayer p, LivingEntity victim, boolean victimWasBoss) {
		for (Active a : active(p)) {
			a.effect().onKill(context(p, a), victim, victimWasBoss);
		}
	}

	public boolean tryResurrect(ServerPlayer p) {
		for (Active a : active(p)) {
			if (a.effect().onLethal(context(p, a))) {
				return true;
			}
		}
		return false;
	}

	public void onJump(ServerPlayer p) {
		for (Active a : active(p)) {
			a.effect().onJump(context(p, a));
		}
	}

	public boolean has(ServerPlayer p, String id) {
		for (Active a : active(p)) {
			if (a.effect().id().equals(id)) {
				return true;
			}
		}
		return false;
	}

	public boolean preventsSleep(ServerPlayer p) {
		for (Active a : active(p)) {
			if (a.effect().preventsSleep()) {
				return true;
			}
		}
		return false;
	}

	public double rewardMultiplier(@Nullable ServerPlayer p) {
		if (p == null) {
			return 1.0;
		}
		double mult = 1.0;
		for (Active a : active(p)) {
			mult *= a.effect().rewardChanceMultiplier();
		}
		return mult;
	}
}
