package com.bloomcraft.bosses.nemesis;

import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.core.Data;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything remembered about one Nemesis. Saved with the world (bloomcraft/world.dat), so a Nemesis survives
 * reloads even though its mob is never saved in the world.
 */
public final class NemesisRecord {

	public final String id;
	public final UUID owner;
	public String ownerName;
	public EntityType<?> type;
	public BossRank baseRank;
	/** The current Challenge note's number: only that copy works (a stashed older copy is void). */
	public int challengeSerial;
	/** It killed its owner and they haven't been handed its Challenge yet (they respawn with it). */
	public boolean challengePending;
	/** The rank it was found at in the wild, before anyone raised it with Souls (null: unknown). */
	public @Nullable BossRank wildRank;
	public BossRank rank;
	public int baseTraitCount;
	public List<String> traits = new ArrayList<>();
	public List<String> titles = new ArrayList<>();
	public int level = 1;
	public int kills;
	public int escapes;
	public int returns;
	public int meleeHits;
	public int rangedHits;
	public long createdAt;
	public long returnAt;
	/** Its own name ("Scrawl"), unique in the world. */
	public String name = "";
	/** What it was known for as a boss ("Bulwark" -> "Scrawl the Bulwark"). */
	public String epithet = "";
	/** Body kept the same between returns: baby or adult, slime size, what it held. */
	public boolean baby;
	public int size = -1;
	public String hand = "";
	/** Gear tier it already wore as a boss, so its Nemesis gear never looks worse (0 chain .. 3 netherite). */
	public int gearFloor;
	/** Levels gained from killing other mobs during the current outing (capped per outing). */
	public int outingKills;
	/** What it has adapted to: kind of damage -> 1 (resists it) or 2 (immune). */
	public final Map<DamageKind, Integer> adapted = new EnumMap<>(DamageKind.class);
	/** Its Terms of Surrender: die this way carrying its Challenge and the feud ends. */
	public Surrender surrender = Surrender.FALL;

	/** Runtime only: tick (mod clock) until which it prowls (0 = not prowling). */
	public int prowlUntil;
	/** Runtime only: someone other than its prey who attacked it while prowling (it fights back). */
	public @Nullable UUID provoker;
	/** Runtime only: the living mob while the Nemesis is in the world. */
	public @Nullable UUID entity;
	/** Runtime only: ticks the owner has been out of reach. */
	public int lostTicks;
	/** Runtime only: damage taken by kind since it last adapted (it adapts to the biggest one). */
	public final Map<DamageKind, Double> recentDamage = new EnumMap<>(DamageKind.class);
	/** Runtime only: the player it is after right now (a teammate can pull it off its owner). */
	public @Nullable UUID focus;
	/** Runtime only: tick when it last killed its owner (its owner then respawning far away isn't fleeing). */
	public int ownerDownTick = -100000;

	public NemesisRecord(String id, UUID owner, String ownerName, EntityType<?> type, BossRank rank) {
		this.id = id;
		this.owner = owner;
		this.ownerName = ownerName;
		this.type = type;
		this.baseRank = rank;
		this.rank = rank;
	}

	/** How many hard immunities it has (fire doesn't count: it's free). */
	public int immunities() {
		int n = 0;
		for (Map.Entry<DamageKind, Integer> e : adapted.entrySet()) {
			if (e.getValue() >= 2 && e.getKey() != DamageKind.FIRE) {
				n++;
			}
		}
		return n;
	}

	public String title() {
		return titles.isEmpty() ? "Vengeful" : titles.get(titles.size() - 1);
	}

	public CompoundTag save() {
		CompoundTag s = new CompoundTag();
		s.putString("owner", owner.toString());
		s.putString("owner_name", ownerName);
		s.putString("type", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
		s.putString("base_rank", baseRank.name());
		s.putString("rank", rank.name());
		s.putInt("base_trait_count", baseTraitCount);
		Data.strings(s, "traits", traits);
		Data.strings(s, "titles", titles);
		s.putInt("level", level);
		s.putInt("kills", kills);
		s.putInt("escapes", escapes);
		s.putInt("returns", returns);
		s.putInt("melee_hits", meleeHits);
		s.putInt("ranged_hits", rangedHits);
		s.putLong("created_at", createdAt);
		s.putLong("return_at", returnAt);
		s.putString("name", name);
		s.putString("epithet", epithet);
		s.putBoolean("baby", baby);
		s.putInt("size", size);
		s.putString("hand", hand);
		s.putInt("gear_floor", gearFloor);
		s.putInt("challenge_serial", challengeSerial);
		s.putBoolean("challenge_pending", challengePending);
		if (wildRank != null) {
			s.putString("wild_rank", wildRank.name());
		}
		s.putString("surrender", surrender.name());
		CompoundTag adapt = new CompoundTag();
		adapted.forEach((k, v) -> adapt.putInt(k.name(), v));
		s.put("adapted", adapt);
		return s;
	}

	public static @Nullable NemesisRecord load(String id, CompoundTag s) {
		try {
			Identifier typeId = Identifier.tryParse(s.getStringOr("type", ""));
			EntityType<?> type = typeId == null ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(typeId).orElse(null);
			BossRank base = BossRank.parse(s.getStringOr("base_rank", ""));
			BossRank rank = BossRank.parse(s.getStringOr("rank", ""));
			if (type == null || base == null || rank == null) {
				return null;
			}
			NemesisRecord r = new NemesisRecord(id, UUID.fromString(s.getStringOr("owner", "")),
				s.getStringOr("owner_name", "?"), type, base);
			r.rank = rank;
			r.baseTraitCount = s.getIntOr("base_trait_count", 0);
			r.traits = new ArrayList<>(Data.strings(s, "traits"));
			r.titles = new ArrayList<>(Data.strings(s, "titles"));
			r.level = s.getIntOr("level", 1);
			r.kills = s.getIntOr("kills", 0);
			r.escapes = s.getIntOr("escapes", 0);
			r.returns = s.getIntOr("returns", 0);
			r.meleeHits = s.getIntOr("melee_hits", 0);
			r.rangedHits = s.getIntOr("ranged_hits", 0);
			r.createdAt = s.getLongOr("created_at", 0L);
			r.returnAt = s.getLongOr("return_at", 0L);
			r.name = s.getStringOr("name", "");
			r.epithet = s.getStringOr("epithet", "");
			r.baby = s.getBooleanOr("baby", false);
			r.size = s.getIntOr("size", -1);
			r.hand = s.getStringOr("hand", "");
			r.gearFloor = s.getIntOr("gear_floor", 0);
			r.challengeSerial = s.getIntOr("challenge_serial", 0);
			r.challengePending = s.getBooleanOr("challenge_pending", false);
			r.wildRank = BossRank.parse(s.getStringOr("wild_rank", ""));
			Surrender surrender = Surrender.parse(s.getStringOr("surrender", ""));
			r.surrender = surrender != null ? surrender : Surrender.pick(type);
			CompoundTag adapt = s.getCompoundOrEmpty("adapted");
			for (String key : adapt.keySet()) {
				DamageKind kind = DamageKind.parse(key);
				if (kind != null) {
					r.adapted.put(kind, Math.max(1, Math.min(2, adapt.getIntOr(key, 1))));
				}
			}
			return r;
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
