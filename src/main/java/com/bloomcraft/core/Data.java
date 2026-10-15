package com.bloomcraft.core;

import com.bloomcraft.BloomCraft;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Saved data on entities: one compound tag per entity, saved with it (players keep theirs through death).
 * The mod keeps its own keys in it, like a plugin's persistent data container.
 */
public final class Data {

	/** Mobs and other entities. */
	public static final AttachmentType<CompoundTag> ENTITY = AttachmentRegistry.create(BloomCraft.id("data"),
		b -> b.initializer(CompoundTag::new).persistent(CompoundTag.CODEC));

	/** Players: kept through death and respawn. */
	public static final AttachmentType<CompoundTag> PLAYER = AttachmentRegistry.create(BloomCraft.id("player"),
		b -> b.initializer(CompoundTag::new).persistent(CompoundTag.CODEC).copyOnDeath());

	private Data() {
	}

	public static void init() {
		// (static fields above register the attachment types)
	}

	private static AttachmentType<CompoundTag> type(Entity e) {
		return e instanceof Player ? PLAYER : ENTITY;
	}

	/** The entity's data (created if it has none yet). Changes are saved with the entity. */
	public static CompoundTag of(Entity e) {
		return e.getAttachedOrCreate(type(e));
	}

	/** The entity's data, or null if it has none (doesn't create any). */
	public static @Nullable CompoundTag peek(Entity e) {
		return e.getAttached(type(e));
	}

	public static boolean has(Entity e, String key) {
		CompoundTag tag = peek(e);
		return tag != null && tag.contains(key);
	}

	public static void remove(Entity e, String key) {
		CompoundTag tag = peek(e);
		if (tag != null) {
			tag.remove(key);
		}
	}

	public static String str(Entity e, String key, String def) {
		CompoundTag tag = peek(e);
		return tag == null ? def : tag.getStringOr(key, def);
	}

	public static @Nullable String str(Entity e, String key) {
		CompoundTag tag = peek(e);
		return tag == null ? null : tag.getString(key).orElse(null);
	}

	public static int integer(Entity e, String key, int def) {
		CompoundTag tag = peek(e);
		return tag == null ? def : tag.getIntOr(key, def);
	}

	public static long longValue(Entity e, String key, long def) {
		CompoundTag tag = peek(e);
		return tag == null ? def : tag.getLongOr(key, def);
	}

	public static double dbl(Entity e, String key, double def) {
		CompoundTag tag = peek(e);
		return tag == null ? def : tag.getDoubleOr(key, def);
	}

	public static boolean flag(Entity e, String key) {
		CompoundTag tag = peek(e);
		return tag != null && tag.getBooleanOr(key, false);
	}

	public static void flag(Entity e, String key, boolean value) {
		if (value) {
			of(e).putBoolean(key, true);
		} else {
			remove(e, key);
		}
	}

	public static List<String> strings(CompoundTag tag, String key) {
		List<String> out = new ArrayList<>();
		for (Tag t : tag.getListOrEmpty(key)) {
			t.asString().ifPresent(out::add);
		}
		return out;
	}

	public static void strings(CompoundTag tag, String key, List<String> values) {
		ListTag list = new ListTag();
		for (String v : values) {
			list.add(StringTag.valueOf(v));
		}
		tag.put(key, list);
	}
}
