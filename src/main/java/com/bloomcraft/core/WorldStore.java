package com.bloomcraft.core;

import com.bloomcraft.BloomCraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * The mod's own save file for things that belong to the world rather than to a mob or a player (Nemesis records,
 * waystones, the day counter): {@code <world>/bloomcraft/world.dat}.
 */
public final class WorldStore {

	private static CompoundTag root = new CompoundTag();
	private static @Nullable Path file;
	private static boolean dirty;

	private WorldStore() {
	}

	/** Server starting: reads the world's file (an empty store if there is none yet). */
	public static void load(MinecraftServer server) {
		file = server.getWorldPath(LevelResource.ROOT).resolve("bloomcraft").resolve("world.dat");
		root = new CompoundTag();
		dirty = false;
		if (Files.exists(file)) {
			try {
				root = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
			} catch (IOException | RuntimeException ex) {
				BloomCraft.LOG.error("Could not read {} (starting fresh, the old file is kept as world.dat.broken)", file, ex);
				try {
					Files.copy(file, file.resolveSibling("world.dat.broken"), StandardCopyOption.REPLACE_EXISTING);
				} catch (IOException ignored) {
				}
			}
		}
		Clock.load(root.getLongOr("clock_offset", 0L));
	}

	/** The named section (created if missing). Call {@link #markDirty} after changing it. */
	public static CompoundTag section(String name) {
		CompoundTag tag = root.getCompoundOrEmpty(name);
		root.put(name, tag);
		return tag;
	}

	public static void put(String name, CompoundTag tag) {
		root.put(name, tag);
		dirty = true;
	}

	public static void markDirty() {
		dirty = true;
	}

	/** Writes the file if anything changed (or always, with {@code force}). */
	public static void save(boolean force) {
		if (file == null || (!dirty && !force && root.getLongOr("clock_offset", 0L) == Clock.offset())) {
			return;
		}
		root.putLong("clock_offset", Clock.offset());
		try {
			Files.createDirectories(file.getParent());
			Path temp = file.resolveSibling("world.dat.tmp");
			NbtIo.writeCompressed(root, temp);
			try {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (java.nio.file.AtomicMoveNotSupportedException ex) {
				Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
			}
			dirty = false;
		} catch (IOException ex) {
			BloomCraft.LOG.error("Could not save {}", file, ex);
		}
	}

	/** Server stopped. */
	public static void close() {
		save(false);
		file = null;
		root = new CompoundTag();
	}
}
