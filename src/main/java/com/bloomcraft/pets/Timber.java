package com.bloomcraft.pets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Pig's bonus: chop the bottom log of a tree with an axe and the whole tree comes down. Huge mushrooms and
 * nether "trees" (crimson and warped fungi) come down whole, caps and wart blocks included.
 *
 * <p>Only natural trees: a log tree needs leaves that grew there (placed leaves don't count), so log cabins are
 * safe. Sneak to chop a single block. Every extra block is broken as if you broke it yourself (it costs the axe
 * durability, and drops what your axe would get).</p>
 */
final class Timber {

	private enum Kind { LOG, MUSHROOM, FUNGUS }

	/** How far a tree may spread sideways from its trunk. */
	private static final int REACH = 6;

	/** What a tree, huge mushroom or fungus grows on. */
	private static final Set<Block> SOIL = Set.of(Blocks.DIRT, Blocks.GRASS_BLOCK, Blocks.COARSE_DIRT, Blocks.ROOTED_DIRT,
		Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MUD, Blocks.MOSS_BLOCK, Blocks.PALE_MOSS_BLOCK, Blocks.MANGROVE_ROOTS,
		Blocks.MUDDY_MANGROVE_ROOTS, Blocks.FARMLAND, Blocks.SAND, Blocks.RED_SAND, Blocks.CLAY, Blocks.SNOW_BLOCK,
		Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM, Blocks.NETHERRACK, Blocks.SOUL_SOIL);

	private final Pets mod;
	private boolean felling;

	Timber(Pets mod) {
		this.mod = mod;
	}

	/** A block was broken: with a Pig out and an axe in hand, the rest of the tree comes down too. */
	void chopped(ServerPlayer p, ServerLevel level, BlockPos start, BlockState state) {
		if (felling || p.isShiftKeyDown() || p.isSpectator()) {
			return;
		}
		PetManager.Active a = mod.pets().active(p.getUUID());
		if (a == null || a.pet.species != Species.PIG || a.fainting || mod.happiness().sad(a.pet)) {
			return;
		}
		Kind kind = kind(state);
		if (kind == null || !p.getMainHandItem().is(ItemTags.AXES)) {
			return;
		}
		int limit = (int) Math.round(64 * a.pet.power());
		List<BlockPos> tree = collect(level, start, state.getBlock(), kind, limit);
		if (tree.isEmpty()) {
			return;
		}
		felling = true;
		try {
			Block trunk = state.getBlock();
			for (BlockPos pos : tree) {
				if (!p.getMainHandItem().is(ItemTags.AXES)) {
					break; // the axe broke
				}
				if (!part(level.getBlockState(pos), trunk, kind)) {
					continue; // already gone
				}
				p.gameMode.destroyBlock(pos); // like chopping it yourself: drops, durability, stats
			}
		} finally {
			felling = false;
		}
	}

	private static @Nullable Kind kind(BlockState state) {
		if (state.is(Blocks.CRIMSON_STEM) || state.is(Blocks.WARPED_STEM)) {
			return Kind.FUNGUS;
		}
		if (state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK) || state.is(Blocks.RED_MUSHROOM_BLOCK)) {
			return Kind.MUSHROOM;
		}
		String name = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		if (state.is(BlockTags.LOGS) && !name.startsWith("stripped_") && !name.endsWith("_wood") && !name.endsWith("_hyphae")) {
			return Kind.LOG;
		}
		return null;
	}

	/** Whether this block belongs to the same tree as the one that was chopped. */
	private static boolean part(BlockState state, Block trunk, Kind kind) {
		return switch (kind) {
			case LOG -> state.is(trunk);
			case MUSHROOM -> state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
				|| state.is(Blocks.RED_MUSHROOM_BLOCK);
			case FUNGUS -> state.is(trunk) || state.is(BlockTags.WART_BLOCKS) || state.is(Blocks.SHROOMLIGHT);
		};
	}

	/** The trunk stands on soil: follow it down from the cut (a log cabin's walls don't grow out of dirt). */
	private static boolean rooted(ServerLevel level, BlockPos start, Block trunk, Kind kind) {
		BlockPos b = start;
		for (int i = 0; i < 40; i++) {
			BlockPos below = b.below();
			BlockState state = level.getBlockState(below);
			if (state.is(trunk) || (kind == Kind.MUSHROOM && state.is(Blocks.MUSHROOM_STEM))) {
				b = below;
				continue;
			}
			return SOIL.contains(state.getBlock());
		}
		return false;
	}

	/**
	 * The rest of the tree above and around the chopped block (not the block itself), or nothing if it doesn't
	 * look like a tree that grew there.
	 */
	private static List<BlockPos> collect(ServerLevel level, BlockPos start, Block trunk, Kind kind, int limit) {
		if (!rooted(level, start, trunk, kind)) {
			return List.of();
		}
		Set<BlockPos> seen = new HashSet<>();
		Deque<BlockPos> queue = new ArrayDeque<>();
		List<BlockPos> found = new ArrayList<>();
		seen.add(start);
		queue.add(start);
		int leaves = 0;
		boolean natural = false;
		while (!queue.isEmpty() && found.size() < limit) {
			BlockPos b = queue.poll();
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 1; dy++) {
					for (int dz = -1; dz <= 1; dz++) {
						if (dx == 0 && dy == 0 && dz == 0) {
							continue;
						}
						BlockPos n = b.offset(dx, dy, dz);
						if (n.getY() < start.getY() || Math.abs(n.getX() - start.getX()) > REACH
							|| Math.abs(n.getZ() - start.getZ()) > REACH) {
							continue; // never below the cut, never far to the side (that's a build, not a tree)
						}
						BlockState state = level.getBlockState(n);
						if (kind == Kind.LOG && state.getBlock() instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT)
							&& ++leaves >= 3) {
							natural = true; // leaves that grew there (placed leaves are "persistent")
						}
						if (kind != Kind.LOG && !state.is(trunk) && part(state, trunk, kind)) {
							natural = true; // caps or wart blocks: a real huge mushroom / fungus
						}
						if (!part(state, trunk, kind) || !seen.add(n)) {
							continue;
						}
						found.add(n);
						queue.add(n);
						if (found.size() >= limit) {
							break;
						}
					}
				}
			}
		}
		if (!natural) {
			return List.of();
		}
		found.sort(Comparator.comparingInt(BlockPos::getY));
		return found;
	}
}
