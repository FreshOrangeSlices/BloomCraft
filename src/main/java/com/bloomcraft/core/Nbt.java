package com.bloomcraft.core;

import com.mojang.serialization.DynamicOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Saving items and whole entities as NBT, for the mod's own save files. */
public final class Nbt {

	private Nbt() {
	}

	public static DynamicOps<Tag> ops(HolderLookup.Provider registries) {
		return registries.createSerializationContext(NbtOps.INSTANCE);
	}

	/** An item as a tag (null for an empty stack). */
	public static @Nullable Tag item(ItemStack stack, HolderLookup.Provider registries) {
		if (stack.isEmpty()) {
			return null;
		}
		return ItemStack.CODEC.encodeStart(ops(registries), stack).result().orElse(null);
	}

	/** An item read back (empty if it can't be read, e.g. an item from a mod that's gone). */
	public static ItemStack item(@Nullable Tag tag, HolderLookup.Provider registries) {
		if (tag == null) {
			return ItemStack.EMPTY;
		}
		return ItemStack.CODEC.parse(ops(registries), tag).result().orElse(ItemStack.EMPTY);
	}

	/** Everything about an entity (its look, health, gear, effects...), without its type id. */
	public static CompoundTag entity(Entity entity) {
		TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
		entity.saveWithoutId(out);
		return out.buildResult();
	}

	/** Reads saved entity data into an entity that was just created (before it's added to the world). */
	public static void load(Entity entity, CompoundTag tag) {
		ValueInput in = TagValueInput.create(ProblemReporter.DISCARDING, entity.registryAccess(), tag);
		entity.load(in);
	}

	public static @Nullable UUID uuid(@Nullable String raw) {
		if (raw == null || raw.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(raw);
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
