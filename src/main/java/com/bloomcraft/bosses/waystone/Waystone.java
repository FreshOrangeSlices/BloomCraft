package com.bloomcraft.bosses.waystone;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** One placed waystone. Saved with the world (bloomcraft/world.dat). */
public final class Waystone {

	public final String id;
	public String name;
	public final UUID owner;
	public String ownerName;
	public final ResourceKey<Level> dimension;
	public final BlockPos pos;
	public Item icon = Items.LODESTONE;

	public Waystone(String id, String name, UUID owner, String ownerName, ResourceKey<Level> dimension, BlockPos pos) {
		this.id = id;
		this.name = name;
		this.owner = owner;
		this.ownerName = ownerName;
		this.dimension = dimension;
		this.pos = pos.immutable();
	}

	public static String key(ResourceKey<Level> dimension, BlockPos pos) {
		return dimension.identifier() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
	}

	public String key() {
		return key(dimension, pos);
	}

	public @Nullable ServerLevel level(MinecraftServer server) {
		return server.getLevel(dimension);
	}

	public CompoundTag save() {
		CompoundTag s = new CompoundTag();
		s.putString("name", name);
		s.putString("owner", owner.toString());
		s.putString("owner_name", ownerName);
		s.putString("dimension", dimension.identifier().toString());
		s.putInt("x", pos.getX());
		s.putInt("y", pos.getY());
		s.putInt("z", pos.getZ());
		s.putString("icon", BuiltInRegistries.ITEM.getKey(icon).toString());
		return s;
	}

	public static @Nullable Waystone load(String id, CompoundTag s) {
		try {
			Identifier dim = Identifier.tryParse(s.getStringOr("dimension", "minecraft:overworld"));
			if (dim == null) {
				return null;
			}
			Waystone w = new Waystone(id, s.getStringOr("name", "Waystone"), UUID.fromString(s.getStringOr("owner", "")),
				s.getStringOr("owner_name", "?"), ResourceKey.create(Registries.DIMENSION, dim),
				new BlockPos(s.getIntOr("x", 0), s.getIntOr("y", 0), s.getIntOr("z", 0)));
			Identifier icon = Identifier.tryParse(s.getStringOr("icon", "minecraft:lodestone"));
			if (icon != null) {
				BuiltInRegistries.ITEM.getOptional(icon).filter(i -> i != Items.AIR).ifPresent(i -> w.icon = i);
			}
			return w;
		} catch (IllegalArgumentException ex) {
			return null;
		}
	}
}
