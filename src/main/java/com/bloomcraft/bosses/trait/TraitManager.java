package com.bloomcraft.bosses.trait;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.bosses.trait.impl.BerserkTrait;
import com.bloomcraft.bosses.trait.impl.BlinkingTrait;
import com.bloomcraft.bosses.trait.impl.BulwarkTrait;
import com.bloomcraft.bosses.trait.impl.ChargingTrait;
import com.bloomcraft.bosses.trait.impl.DeadeyeTrait;
import com.bloomcraft.bosses.trait.impl.ExecutionerTrait;
import com.bloomcraft.bosses.trait.impl.FrostboundTrait;
import com.bloomcraft.bosses.trait.impl.GraviticTrait;
import com.bloomcraft.bosses.trait.impl.InfernalTrait;
import com.bloomcraft.bosses.trait.impl.LeapingTrait;
import com.bloomcraft.bosses.trait.impl.QuakingTrait;
import com.bloomcraft.bosses.trait.impl.RegeneratingTrait;
import com.bloomcraft.bosses.trait.impl.ShadowedTrait;
import com.bloomcraft.bosses.trait.impl.SummonerTrait;
import com.bloomcraft.bosses.trait.impl.SwiftTrait;
import com.bloomcraft.bosses.trait.impl.ThornedTrait;
import com.bloomcraft.bosses.trait.impl.UndyingTrait;
import com.bloomcraft.bosses.trait.impl.VampiricTrait;
import com.bloomcraft.bosses.trait.impl.VenomousTrait;
import com.bloomcraft.bosses.trait.impl.VolatileTrait;
import com.bloomcraft.bosses.trait.impl.VolleyTrait;
import com.bloomcraft.bosses.trait.impl.WardedTrait;
import com.bloomcraft.core.Cfg;
import com.bloomcraft.core.Rng;
import net.minecraft.world.entity.LivingEntity;
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

/** Registry of boss traits, and the logic that rolls a compatible set of them for a new boss. */
public final class TraitManager {

	private final Map<String, BossTrait> registry = new LinkedHashMap<>();
	private final Set<String> enabled = new HashSet<>();
	private final Map<String, Double> weights = new HashMap<>();
	private final Set<String> blockedPairs = new HashSet<>();

	public TraitManager() {
		// Offense
		register(new BerserkTrait());
		register(new VampiricTrait());
		register(new VenomousTrait());
		register(new ExecutionerTrait());
		register(new DeadeyeTrait());
		register(new VolleyTrait());
		// Defense
		register(new BulwarkTrait());
		register(new RegeneratingTrait());
		register(new WardedTrait());
		register(new UndyingTrait());
		register(new ThornedTrait());
		// Movement
		register(new SwiftTrait());
		register(new LeapingTrait());
		register(new BlinkingTrait());
		register(new ChargingTrait());
		// Control
		register(new FrostboundTrait());
		register(new ShadowedTrait());
		register(new GraviticTrait());
		register(new QuakingTrait());
		// Special
		register(new SummonerTrait());
		register(new VolatileTrait());
		register(new InfernalTrait());
	}

	public void register(BossTrait trait) {
		registry.put(trait.id(), trait);
	}

	public void load(Cfg root) {
		enabled.clear();
		weights.clear();
		blockedPairs.clear();
		for (BossTrait trait : registry.values()) {
			Cfg s = root.sectionOrEmpty(trait.id());
			// Swift only made bosses run faster than players: off unless the config turns it on
			if (s.getBool("enabled", !trait.id().equals("swift"))) {
				enabled.add(trait.id());
			}
			weights.put(trait.id(), Math.max(0, s.getDouble("weight", 10)));
			trait.load(s);
		}
		for (String key : root.keys()) {
			if (!key.equals("incompatible") && !key.equals("area-damage-cap") && !registry.containsKey(key)) {
				BloomCraft.LOG.warn("traits.{} does not match any trait (ignored)", key);
			}
		}
		for (Object entry : root.getList("incompatible")) {
			if (entry instanceof List<?> pair && pair.size() == 2) {
				blockedPairs.add(pairKey(String.valueOf(pair.get(0)), String.valueOf(pair.get(1))));
			}
		}
	}

	public @Nullable BossTrait get(String id) {
		return registry.get(id.toLowerCase(Locale.ROOT));
	}

	public Collection<BossTrait> all() {
		return registry.values();
	}

	public boolean isEnabled(BossTrait trait) {
		return enabled.contains(trait.id());
	}

	public List<BossTrait> enabledTraits() {
		List<BossTrait> out = new ArrayList<>();
		for (BossTrait trait : registry.values()) {
			if (enabled.contains(trait.id())) {
				out.add(trait);
			}
		}
		return out;
	}

	public boolean compatible(BossTrait a, BossTrait b) {
		if (a.id().equals(b.id())) {
			return false;
		}
		if (a.incompatibleWith().contains(b.id()) || b.incompatibleWith().contains(a.id())) {
			return false;
		}
		return !blockedPairs.contains(pairKey(a.id(), b.id()));
	}

	/** Weighted roll without repeats, skipping traits the mob can't use or that clash with ones already picked. */
	public List<BossTrait> roll(LivingEntity entity, int count) {
		List<BossTrait> picked = new ArrayList<>();
		List<BossTrait> pool = new ArrayList<>();
		for (BossTrait trait : enabledTraits()) {
			if (trait.supports(entity) && weights.getOrDefault(trait.id(), 0.0) > 0) {
				pool.add(trait);
			}
		}
		while (picked.size() < count && !pool.isEmpty()) {
			BossTrait next = Rng.weighted(pool, t -> weights.getOrDefault(t.id(), 0.0));
			if (next == null) {
				break;
			}
			pool.remove(next);
			boolean ok = true;
			for (BossTrait chosen : picked) {
				if (!compatible(chosen, next)) {
					ok = false;
					break;
				}
			}
			if (ok) {
				picked.add(next);
			}
		}
		return picked;
	}

	/** One extra trait the mob supports that doesn't clash with what it has (or null). */
	public @Nullable BossTrait rollExtra(LivingEntity entity, List<BossTrait> existing) {
		List<BossTrait> pool = new ArrayList<>();
		for (BossTrait trait : enabledTraits()) {
			if (!trait.supports(entity) || weights.getOrDefault(trait.id(), 0.0) <= 0) {
				continue;
			}
			boolean ok = true;
			for (BossTrait have : existing) {
				if (!compatible(have, trait)) {
					ok = false;
					break;
				}
			}
			if (ok) {
				pool.add(trait);
			}
		}
		return Rng.weighted(pool, t -> weights.getOrDefault(t.id(), 0.0));
	}

	private static String pairKey(String a, String b) {
		a = a.trim().toLowerCase(Locale.ROOT);
		b = b.trim().toLowerCase(Locale.ROOT);
		return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
	}
}
