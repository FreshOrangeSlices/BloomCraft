package com.bloomcraft.pets;

import com.bloomcraft.core.Nbt;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * One pet a player owns. While it's in its bloom, the pet lives entirely in here: {@link #snapshot} is the whole mob
 * (its look, variant, health, the Vex's gear...) and it comes back exactly the same when summoned.
 */
public final class Pet {

	/** How a small pet is ridden, decided once when it is tamed. */
	public enum RideStyle {
		/** Big enough already. */
		NORMAL,
		/** The pet grows to carry you. */
		GROW,
		/** You shrink to ride it. */
		SHRINK
	}

	/** Where a stationed pet keeps its post. */
	public record Station(ResourceKey<Level> dimension, Vec3 pos, float yaw) {

		public @Nullable ServerLevel level(MinecraftServer server) {
			return server.getLevel(dimension);
		}

		public BlockPos block() {
			return BlockPos.containing(pos);
		}

		CompoundTag save() {
			CompoundTag tag = new CompoundTag();
			tag.putString("dim", dimension.identifier().toString());
			tag.putDouble("x", pos.x);
			tag.putDouble("y", pos.y);
			tag.putDouble("z", pos.z);
			tag.putFloat("yaw", yaw);
			return tag;
		}

		static @Nullable Station load(CompoundTag tag) {
			Identifier dim = Identifier.tryParse(tag.getStringOr("dim", ""));
			if (dim == null) {
				return null;
			}
			return new Station(ResourceKey.create(Registries.DIMENSION, dim),
				new Vec3(tag.getDoubleOr("x", 0), tag.getDoubleOr("y", 0), tag.getDoubleOr("z", 0)), tag.getFloatOr("yaw", 0));
		}
	}

	public static final int MAX_LEVEL = 10;

	public final UUID id;
	public final UUID owner;
	public final Species species;
	public String name;
	public int level = 1;
	public int xp;
	public RideStyle rideStyle;
	/** Epoch millis until which a fainted pet rests. */
	public long restingUntil;
	/** XP an Allay has gathered towards its next Bottle o' Enchanting. */
	public int xpBank;
	/** Order in the pet menu. */
	public int order;
	/** Health as a share of max health when it was put away (-1: unknown, use what the snapshot says). */
	public double health = -1;
	/** How happy it is, 0-100 (players only ever see it as 1-10 hearts). */
	public double happiness = 80;
	/** How long it has been out with its owner since it last rested in its bloom (millis). */
	public long tiredMillis;
	/** When it went back into its bloom (0: it's out, or unknown). Resting there cheers it up. */
	public long stashedAt;
	/** Until when it "loves you" (its mood drops slower). Lost if it faints. */
	public long lovedUntil;
	/** Where this pet is stationed, or null if it comes with its owner. */
	public @Nullable Station station;
	/** The whole mob as it was put away (null: a fresh one of its species). */
	public @Nullable CompoundTag snapshot;
	/** What it carries (always the size of a max-level pet's storage; only {@link #storageSlots} are open). */
	public final SimpleContainer storage;

	// not saved: cooldowns and what the owner was last told
	public long lastFeedXp;
	public long lastFetchXp;
	public long lastCombatXp;
	public @Nullable Boolean wasSad;
	public @Nullable Boolean wasTired;

	public Pet(UUID id, UUID owner, Species species, String name, RideStyle rideStyle) {
		this.id = id;
		this.owner = owner;
		this.species = species;
		this.name = name;
		this.rideStyle = rideStyle;
		this.storage = new SimpleContainer(Math.max(1, species.storage().slots(MAX_LEVEL)));
	}

	/** Bond XP needed to go from this level to the next. */
	public static int xpToNext(int level) {
		return 40 + 30 * level;
	}

	public boolean maxed() {
		return level >= MAX_LEVEL;
	}

	/** 1.0 at level 1, a little more each level (1.45 at level 10): how much stronger its bonus and stats are. */
	public double power() {
		return 1.0 + 0.05 * (level - 1);
	}

	public int storageSlots() {
		return species.storage().slots(level);
	}

	public boolean resting(long now) {
		return restingUntil > now;
	}

	public boolean stationed() {
		return station != null;
	}

	// =====================================================================
	//  Saving
	// =====================================================================

	public CompoundTag save(HolderLookup.Provider registries) {
		CompoundTag tag = new CompoundTag();
		tag.putString("name", name);
		tag.putString("species", species.name());
		tag.putInt("level", level);
		tag.putInt("xp", xp);
		tag.putString("ride_style", rideStyle.name());
		tag.putLong("resting_until", restingUntil);
		tag.putInt("xp_bank", xpBank);
		tag.putInt("order", order);
		tag.putDouble("health", health);
		tag.putDouble("happiness", Math.round(happiness * 100) / 100.0);
		tag.putLong("tired_millis", tiredMillis);
		tag.putLong("stashed_at", stashedAt);
		tag.putLong("loved_until", lovedUntil);
		if (station != null) {
			tag.put("station", station.save());
		}
		if (snapshot != null) {
			tag.put("snapshot", snapshot);
		}
		ListTag items = new ListTag();
		for (int slot = 0; slot < storage.getContainerSize(); slot++) {
			ItemStack stack = storage.getItem(slot);
			Tag item = Nbt.item(stack, registries);
			if (item instanceof CompoundTag compound) {
				CompoundTag entry = new CompoundTag();
				entry.putInt("slot", slot);
				entry.put("item", compound);
				items.add(entry);
			}
		}
		tag.put("storage", items);
		return tag;
	}

	/** Reads a pet, or null if this version can't (an unknown species): the caller keeps such entries as they are. */
	public static @Nullable Pet load(UUID owner, String key, CompoundTag tag, HolderLookup.Provider registries) {
		Species species = Species.parse(tag.getStringOr("species", ""));
		UUID id = Nbt.uuid(key);
		if (species == null || id == null) {
			return null;
		}
		RideStyle style;
		try {
			style = RideStyle.valueOf(tag.getStringOr("ride_style", "NORMAL").toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException ex) {
			style = RideStyle.NORMAL;
		}
		Pet pet = new Pet(id, owner, species, tag.getStringOr("name", species.displayName()), style);
		pet.level = Math.max(1, Math.min(MAX_LEVEL, tag.getIntOr("level", 1)));
		pet.xp = Math.max(0, tag.getIntOr("xp", 0));
		pet.restingUntil = tag.getLongOr("resting_until", 0);
		pet.xpBank = Math.max(0, tag.getIntOr("xp_bank", 0));
		pet.order = tag.getIntOr("order", 0);
		pet.health = tag.getDoubleOr("health", -1);
		pet.happiness = Math.max(0, Math.min(100, tag.getDoubleOr("happiness", 80)));
		pet.tiredMillis = Math.max(0, tag.getLongOr("tired_millis", 0));
		pet.stashedAt = Math.max(0, tag.getLongOr("stashed_at", 0));
		pet.lovedUntil = Math.max(0, tag.getLongOr("loved_until", 0));
		pet.station = tag.getCompound("station").map(Station::load).orElse(null);
		pet.snapshot = tag.getCompound("snapshot").orElse(null);
		for (Tag t : tag.getListOrEmpty("storage")) {
			if (t instanceof CompoundTag entry) {
				int slot = entry.getIntOr("slot", -1);
				if (slot >= 0 && slot < pet.storage.getContainerSize()) {
					pet.storage.setItem(slot, Nbt.item(entry.get("item"), registries));
				}
			}
		}
		return pet;
	}
}
