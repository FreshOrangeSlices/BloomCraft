package com.bloomcraft.pets;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.core.Nbt;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Every player's pets, one file per player in the world: {@code <world>/bloomcraft/pets/<uuid>.dat}. A player's
 * file is read the first time it's needed and kept while they're online (or while they have a pet at a post).
 * Changes are written every 30 seconds, when the world saves, and when the player leaves.
 */
public final class PetStore {

	/** One player's pets and what else is kept with them. */
	private static final class Owner {
		final Map<UUID, Pet> pets = new LinkedHashMap<>();
		/** Entries this version can't read (unknown species...). Written back untouched, never dropped. */
		final Map<String, CompoundTag> unreadable = new LinkedHashMap<>();
		/** Kinds of animal this player ever bonded with. */
		final Set<Species> bonded = EnumSet.noneOf(Species.class);
		@Nullable UUID lastPet;
		/** Their Taming level when last seen (their posted pets use it while they're away). */
		int tamingLevel;
		boolean pvp;
	}

	private final Map<UUID, Owner> owners = new HashMap<>();
	private final Set<UUID> dirty = new HashSet<>();
	private @Nullable Path folder;
	private HolderLookup.@Nullable Provider registries;

	/** Server starting: finds the world's pet folder and loads everyone with a pet at a post. */
	public void start(MinecraftServer server) {
		owners.clear();
		dirty.clear();
		folder = server.getWorldPath(LevelResource.ROOT).resolve("bloomcraft").resolve("pets");
		registries = server.registryAccess();
		loadStationed();
	}

	/** Server stopping: everything is written. */
	public void stop() {
		saveAll();
		owners.clear();
		folder = null;
		registries = null;
	}

	public boolean ready() {
		return folder != null && registries != null;
	}

	// =====================================================================
	//  Reading
	// =====================================================================

	/** The player's pets in menu order. */
	public List<Pet> pets(UUID owner) {
		List<Pet> list = new ArrayList<>(owner(owner).pets.values());
		list.sort(Comparator.comparingInt(p -> p.order));
		return list;
	}

	public int count(UUID owner) {
		return owner(owner).pets.size();
	}

	public @Nullable Pet get(UUID owner, UUID petId) {
		return owner(owner).pets.get(petId);
	}

	public @Nullable Pet byName(UUID owner, String name) {
		String wanted = name.trim();
		for (Pet pet : owner(owner).pets.values()) {
			if (pet.name.equalsIgnoreCase(wanted)) {
				return pet;
			}
		}
		return null;
	}

	public boolean nameTaken(UUID owner, String name) {
		return byName(owner, name) != null;
	}

	/** The pet this player summoned last (if it's still theirs). */
	public @Nullable Pet last(UUID owner) {
		Owner o = owner(owner);
		return o.lastPet == null ? null : o.pets.get(o.lastPet);
	}

	/** Every stationed pet (of loaded owners; owners with stationed pets are loaded at startup and stay loaded). */
	public List<Pet> stationed() {
		List<Pet> out = new ArrayList<>();
		for (Owner o : owners.values()) {
			for (Pet pet : o.pets.values()) {
				if (pet.stationed()) {
					out.add(pet);
				}
			}
		}
		return out;
	}

	public Collection<UUID> loadedOwners() {
		return List.copyOf(owners.keySet());
	}

	public int tamingLevel(UUID owner) {
		return owner(owner).tamingLevel;
	}

	public boolean bondedBefore(UUID owner, Species species) {
		return owner(owner).bonded.contains(species);
	}

	/** Whether this player turned on "pets join PvP fights". */
	public boolean pvp(UUID owner) {
		return owner(owner).pvp;
	}

	// =====================================================================
	//  Changing
	// =====================================================================

	public void add(Pet pet) {
		Owner o = owner(pet.owner);
		int order = 0;
		for (Pet p : o.pets.values()) {
			order = Math.max(order, p.order + 1);
		}
		pet.order = order;
		o.pets.put(pet.id, pet);
		o.bonded.add(pet.species);
		save(pet.owner);
	}

	public void remove(Pet pet) {
		Owner o = owner(pet.owner);
		o.pets.remove(pet.id);
		if (pet.id.equals(o.lastPet)) {
			o.lastPet = null;
		}
		save(pet.owner);
	}

	public void setLast(Pet pet) {
		owner(pet.owner).lastPet = pet.id;
		changed(pet.owner);
	}

	public void setTamingLevel(UUID owner, int level) {
		Owner o = owner(owner);
		if (o.tamingLevel != level) {
			o.tamingLevel = level;
			changed(owner);
		}
	}

	public void setPvp(UUID owner, boolean on) {
		owner(owner).pvp = on;
		changed(owner);
	}

	/** Something about this player's pets changed: written soon. */
	public void changed(UUID owner) {
		dirty.add(owner);
	}

	/** The player left: their file is written, and forgotten unless a pet of theirs keeps a post. */
	public void unload(UUID owner) {
		save(owner);
		Owner o = owners.get(owner);
		if (o != null && o.pets.values().stream().noneMatch(Pet::stationed)) {
			owners.remove(owner);
		}
	}

	// =====================================================================
	//  Files
	// =====================================================================

	private Owner owner(UUID id) {
		Owner o = owners.get(id);
		if (o == null) {
			o = new Owner();
			owners.put(id, o);
			read(id, o);
		}
		return o;
	}

	private @Nullable Path file(UUID owner) {
		return folder == null ? null : folder.resolve(owner + ".dat");
	}

	private void read(UUID id, Owner o) {
		Path file = file(id);
		if (file == null || registries == null || !Files.isRegularFile(file)) {
			return;
		}
		CompoundTag root;
		try {
			root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
		} catch (IOException | RuntimeException ex) {
			// keep the damaged file for a human to look at, instead of overwriting it with an empty list
			Path broken = file.resolveSibling(id + ".dat.broken-" + System.currentTimeMillis());
			try {
				Files.move(file, broken);
			} catch (IOException ignored) {
			}
			BloomCraft.LOG.error("Could not read the pets of {} (the file was kept as {})", id, broken.getFileName(), ex);
			return;
		}
		o.lastPet = Nbt.uuid(root.getStringOr("last_pet", ""));
		o.tamingLevel = root.getIntOr("taming_level", 0);
		o.pvp = root.getBooleanOr("pvp", false);
		for (Tag t : root.getListOrEmpty("bonded")) {
			t.asString().map(Species::parse).ifPresent(o.bonded::add);
		}
		CompoundTag pets = root.getCompoundOrEmpty("pets");
		for (String key : pets.keySet()) {
			CompoundTag entry = pets.getCompoundOrEmpty(key);
			Pet pet;
			try {
				pet = Pet.load(id, key, entry, registries);
			} catch (RuntimeException ex) {
				BloomCraft.LOG.warn("Pet {} of {} can't be read; it is kept as is", key, id, ex);
				pet = null;
			}
			if (pet == null) {
				o.unreadable.put(key, entry);
				continue;
			}
			o.pets.put(pet.id, pet);
			o.bonded.add(pet.species);
		}
	}

	/** Writes this player's file now. */
	public void save(UUID owner) {
		dirty.remove(owner);
		Owner o = owners.get(owner);
		Path file = file(owner);
		if (o == null || file == null || registries == null) {
			return;
		}
		CompoundTag root = new CompoundTag();
		root.putInt("version", 1);
		if (o.lastPet != null && o.pets.containsKey(o.lastPet)) {
			root.putString("last_pet", o.lastPet.toString());
		}
		root.putInt("taming_level", o.tamingLevel);
		root.putBoolean("pvp", o.pvp);
		var bonded = new net.minecraft.nbt.ListTag();
		for (Species s : o.bonded) {
			bonded.add(StringTag.valueOf(s.name()));
		}
		root.put("bonded", bonded);
		CompoundTag pets = new CompoundTag();
		for (Pet pet : o.pets.values()) {
			pets.put(pet.id.toString(), pet.save(registries));
		}
		for (Map.Entry<String, CompoundTag> raw : o.unreadable.entrySet()) {
			pets.put(raw.getKey(), raw.getValue());
		}
		root.put("pets", pets);
		try {
			Files.createDirectories(file.getParent());
			// write a temporary file and swap it in, so a crash mid-save never leaves a half-written file
			Path tmp = file.resolveSibling(owner + ".dat.tmp");
			NbtIo.writeCompressed(root, tmp);
			try {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ex) {
				Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException ex) {
			BloomCraft.LOG.error("Could not save the pets of {}", owner, ex);
		}
	}

	/** Writes every player whose pets changed. */
	public void flush() {
		for (UUID owner : List.copyOf(dirty)) {
			save(owner);
		}
	}

	public void saveAll() {
		for (UUID owner : List.copyOf(owners.keySet())) {
			save(owner);
		}
	}

	/** At startup: loads every player who has a stationed pet, so their pets work even while they're offline. */
	private void loadStationed() {
		if (folder == null || !Files.isDirectory(folder)) {
			return;
		}
		try (Stream<Path> files = Files.list(folder)) {
			for (Path file : files.toList()) {
				String name = file.getFileName().toString();
				UUID id = name.endsWith(".dat") ? Nbt.uuid(name.substring(0, name.length() - 4)) : null;
				if (id == null) {
					continue;
				}
				Owner o = owner(id);
				if (o.pets.values().stream().noneMatch(Pet::stationed)) {
					owners.remove(id);
				}
			}
		} catch (IOException ex) {
			BloomCraft.LOG.warn("Couldn't look for stationed pets: {}", ex.toString());
		}
	}
}
