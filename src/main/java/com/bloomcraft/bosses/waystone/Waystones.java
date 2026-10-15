package com.bloomcraft.bosses.waystone;

import com.bloomcraft.BloomCraft;
import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.config.BossSettings;
import com.bloomcraft.bosses.item.BossItems;
import com.bloomcraft.bosses.item.ModItems;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.SafeSpots;
import com.bloomcraft.core.Scheduler;
import com.bloomcraft.core.ServerMenu;
import com.bloomcraft.core.Spawns;
import com.bloomcraft.core.Text;
import com.bloomcraft.core.Ticks;
import com.bloomcraft.core.WorldStore;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Waystones: one public network. Every placed waystone (from any player) reaches every other one, for free, after a
 * short warm-up. Names come from an anvil rename (or a name tag used on the placed stone).
 */
public final class Waystones {

	private static final String STORE = "waystones";
	private static final int PER_PAGE = 45;

	private final Bosses bosses;
	private final Map<String, Waystone> byId = new LinkedHashMap<>();
	private final Map<String, String> byLocation = new HashMap<>();
	private final Map<UUID, Integer> cooldownUntil = new HashMap<>();
	/** Player -> the warm-up they're in (the tick it started, to tell an old warm-up from a new one). */
	private final Map<UUID, Integer> travelling = new HashMap<>();
	/** Waystone -> its floating name (never saved; put back whenever someone comes near). */
	private final Map<String, UUID> labels = new HashMap<>();

	public Waystones(Bosses bosses) {
		this.bosses = bosses;
	}

	private BossSettings f() {
		return bosses.settings();
	}

	private static Map<String, Component> name(String name) {
		return Map.of("name", Component.literal(name));
	}

	// =====================================================================
	// Storage
	// =====================================================================

	public void load() {
		byId.clear();
		byLocation.clear();
		labels.clear();
		travelling.clear();
		cooldownUntil.clear();
		CompoundTag root = WorldStore.section(STORE);
		for (String id : root.keySet()) {
			Waystone w = Waystone.load(id, root.getCompoundOrEmpty(id));
			if (w != null) {
				byId.put(id, w);
				byLocation.put(w.key(), id);
			}
		}
	}

	private void save() {
		CompoundTag root = new CompoundTag();
		for (Waystone w : byId.values()) {
			root.put(w.id, w.save());
		}
		WorldStore.put(STORE, root);
	}

	public int count() {
		return byId.size();
	}

	public List<Waystone> all() {
		return List.copyOf(byId.values());
	}

	public @Nullable Waystone at(Level level, BlockPos pos) {
		String id = byLocation.get(Waystone.key(level.dimension(), pos));
		return id == null ? null : byId.get(id);
	}

	private static boolean isAdmin(ServerPlayer player) {
		return Commands.LEVEL_GAMEMASTERS.check(player.permissions());
	}

	private static boolean ownerOrAdmin(ServerPlayer player, Waystone w) {
		return w.owner.equals(player.getUUID()) || isAdmin(player);
	}

	// =====================================================================
	// Items
	// =====================================================================

	/** A waystone item. {@code name} is kept when a placed waystone is picked back up. */
	public static ItemStack item(@Nullable String name) {
		ItemStack item = new ItemStack(ModItems.WAYSTONE);
		item.set(DataComponents.ITEM_NAME, Component.literal("Waystone").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		if (name != null) {
			item.set(DataComponents.CUSTOM_NAME, Text.noItalic(Component.literal(name).withStyle(ChatFormatting.AQUA)));
		}
		List<Component> lore = new ArrayList<>();
		lore.add(Text.line("Place it to join the waystone network.", ChatFormatting.GRAY));
		lore.add(Text.line("Every waystone, from every player, links", ChatFormatting.GRAY));
		lore.add(Text.line("to every other one. Travel is free.", ChatFormatting.GRAY));
		lore.add(Component.empty());
		lore.add(Text.line("Rename it in an anvil to name it.", ChatFormatting.DARK_GRAY));
		item.set(DataComponents.LORE, new ItemLore(lore));
		BossItems.edit(item, tag -> tag.putString("kind", "waystone"));
		return item;
	}

	// =====================================================================
	// Placing / removing
	// =====================================================================

	/** A waystone block was placed by a player: it joins the network. */
	public Waystone place(ServerPlayer player, BlockPos pos, ItemStack item) {
		Waystone existing = at(player.level(), pos);
		if (existing != null) {
			return existing;
		}
		Component custom = item.get(DataComponents.CUSTOM_NAME);
		String name = custom == null ? null : custom.getString().trim();
		if (name == null || name.isEmpty() || name.equalsIgnoreCase("Waystone")) {
			String base = player.getName().getString() + "'s Waystone";
			name = base;
			int n = 2;
			while (nameTaken(name)) {
				name = base + " " + n++;
			}
		}
		name = name.length() > 32 ? name.substring(0, 32) : name;
		String id = UUID.randomUUID().toString().substring(0, 8);
		Waystone w = new Waystone(id, name, player.getUUID(), player.getName().getString(), player.level().dimension(), pos);
		byId.put(id, w);
		byLocation.put(w.key(), id);
		save();
		ServerLevel level = player.level();
		label(w, level);
		Vec3 c = Vec3.atBottomCenterOf(pos).add(0, 1.2, 0);
		Fx.particle(level, ParticleTypes.END_ROD, c, 40, 0.4, 0.08);
		Fx.play(level, c, "block.respawn_anchor.charge", 1.0f, 1.2f);
		player.sendSystemMessage(f().messages.prefixed("waystone-placed", name(name)));
		return w;
	}

	private boolean nameTaken(String name) {
		for (Waystone w : byId.values()) {
			if (w.name.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	/** Removes the record and its floating name (the block itself is handled by the caller). */
	public void remove(Waystone w) {
		byId.remove(w.id);
		byLocation.remove(w.key());
		removeLabel(w);
		save();
	}

	public void rename(Waystone w, String name) {
		w.name = name.length() > 32 ? name.substring(0, 32) : name;
		save();
		removeLabel(w);
	}

	/** Only the owner (or an admin) may break it; it drops itself, keeping its name. */
	public void registerEvents() {
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) -> {
			if (level.isClientSide() || !(player instanceof ServerPlayer sp) || f() == null) {
				return true;
			}
			Waystone w = at(level, pos);
			if (w != null && !ownerOrAdmin(sp, w)) {
				sp.sendSystemMessage(f().messages.prefixed("waystone-not-owner",
					Map.of("owner", Component.literal(w.ownerName))));
				return false;
			}
			return true;
		});
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (level.isClientSide() || !state.is(ModItems.WAYSTONE_BLOCK) || f() == null) {
				return;
			}
			Waystone w = at(level, pos);
			String name = w == null ? null : w.name;
			if (w != null) {
				remove(w);
				player.sendSystemMessage(f().messages.prefixed("waystone-removed", name(w.name)));
			}
			if (!player.isCreative() && level instanceof ServerLevel server) {
				Vec3 c = Vec3.atCenterOf(pos);
				ItemEntity drop = new ItemEntity(server, c.x, c.y, c.z, item(name));
				drop.setDefaultPickUpDelay();
				server.addFreshEntity(drop);
			}
		});
	}

	// =====================================================================
	// Floating names + upkeep
	// =====================================================================

	private void label(Waystone w, ServerLevel level) {
		Entity old = labels.get(w.id) == null ? null : level.getEntity(labels.get(w.id));
		if (old != null && old.isAlive()) {
			return;
		}
		Display.TextDisplay td = EntityTypes.TEXT_DISPLAY.create(level, EntitySpawnReason.COMMAND);
		if (td == null) {
			return;
		}
		Vec3 at = Vec3.atBottomCenterOf(w.pos).add(0, 1.45, 0);
		td.snapTo(at.x, at.y, at.z, 0, 0);
		td.setText(Component.literal(w.name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
			.append(Component.literal("\n" + w.ownerName).withStyle(ChatFormatting.GRAY)));
		td.setBillboardConstraints(Display.BillboardConstraints.CENTER);
		td.setBackgroundColor(0x50000000);
		Data.flag(td, Spawns.TRANSIENT, true); // never saved: put back whenever someone comes near
		Data.of(td).putString("waystone_label", w.id);
		if (level.addFreshEntity(td)) {
			labels.put(w.id, td.getUUID());
		}
	}

	private void removeLabel(Waystone w) {
		UUID id = labels.remove(w.id);
		MinecraftServer server = Ticks.server();
		if (id == null || server == null) {
			return;
		}
		ServerLevel level = w.level(server);
		Entity e = level == null ? null : level.getEntity(id);
		if (e != null) {
			e.discard();
		}
	}

	/** Every 2 seconds: forget waystones whose block is gone, and put names over the ones players are near. */
	public void upkeep(MinecraftServer server) {
		if (byId.isEmpty()) {
			return;
		}
		boolean changed = false;
		for (Waystone w : List.copyOf(byId.values())) {
			ServerLevel level = w.level(server);
			if (level == null || !level.isLoaded(w.pos)) {
				continue;
			}
			if (!level.getBlockState(w.pos).is(ModItems.WAYSTONE_BLOCK)) {
				byId.remove(w.id);
				byLocation.remove(w.key());
				removeLabel(w);
				changed = true;
				BloomCraft.LOG.info("Waystone '{}' was removed (its block is gone).", w.name);
				continue;
			}
			Vec3 c = Vec3.atCenterOf(w.pos);
			boolean watched = false;
			for (ServerPlayer p : level.players()) {
				if (p.distanceToSqr(c) < 32 * 32) {
					watched = true;
					break;
				}
			}
			if (watched) {
				label(w, level);
			}
		}
		if (changed) {
			save();
		}
	}

	// =====================================================================
	// Using
	// =====================================================================

	/** Right-click on a waystone block: rename (name tag), set its icon (sneak + item) or open the network. */
	public void use(ServerPlayer player, BlockPos pos, ItemStack hand) {
		if (!f().waystonesEnabled) {
			return;
		}
		ServerLevel level = player.level();
		Waystone w = at(level, pos);
		if (w == null) {
			w = place(player, pos, ItemStack.EMPTY); // a waystone that was set without being placed (commands, creative)
		}
		if (hand.is(Items.NAME_TAG) && ownerOrAdmin(player, w)) {
			Component custom = hand.get(DataComponents.CUSTOM_NAME);
			String name = custom == null ? "" : custom.getString().trim();
			if (!name.isEmpty()) {
				rename(w, name);
				label(w, level);
				if (!player.isCreative()) {
					hand.shrink(1);
				}
				Fx.play(level, Vec3.atCenterOf(pos), "block.enchantment_table.use", 1.0f, 1.3f);
				player.sendSystemMessage(f().messages.prefixed("waystone-renamed", name(w.name)));
				return;
			}
		}
		if (player.isShiftKeyDown() && !hand.isEmpty() && ownerOrAdmin(player, w)) {
			w.icon = hand.getItem();
			save();
			Fx.play(level, Vec3.atCenterOf(pos), "entity.item_frame.add_item", 1.0f, 1.0f);
			Fx.actionBar(player, Component.literal("Icon set for " + w.name));
			return;
		}
		openMenu(player, w, 0);
	}

	/** The menu listing every waystone in the world. */
	public void openMenu(ServerPlayer player, @Nullable Waystone from, int page) {
		List<Waystone> list = new ArrayList<>(byId.values());
		UUID me = player.getUUID();
		list.sort(Comparator.<Waystone>comparingInt(w -> from != null && w.id.equals(from.id) ? 0 : w.owner.equals(me) ? 1 : 2)
			.thenComparing(w -> w.name.toLowerCase(Locale.ROOT)));
		int pages = Math.max(1, (list.size() + PER_PAGE - 1) / PER_PAGE);
		int p = Math.max(0, Math.min(pages - 1, page));
		List<String> slots = new ArrayList<>();
		ServerMenu.open(player, Component.literal("Waystones (" + list.size() + ")"), 6, inv -> {
			for (int i = 0; i < PER_PAGE && p * PER_PAGE + i < list.size(); i++) {
				Waystone w = list.get(p * PER_PAGE + i);
				inv.setItem(i, entry(player, w, from != null && w.id.equals(from.id)));
				slots.add(w.id);
			}
			if (p > 0) {
				inv.setItem(45, button(Items.ARROW, "Previous page"));
			}
			inv.setItem(49, button(Items.COMPASS, "Page " + (p + 1) + " / " + pages));
			if (p < pages - 1) {
				inv.setItem(53, button(Items.ARROW, "Next page"));
			}
		}, (who, slot, input, button) -> {
			if (slot == 45 && p > 0) {
				openMenu(who, from, p - 1);
			} else if (slot == 53 && p < pages - 1) {
				openMenu(who, from, p + 1);
			} else if (slot < slots.size()) {
				Waystone target = byId.get(slots.get(slot));
				if (target != null && (from == null || !target.id.equals(from.id))) {
					who.closeContainer();
					travel(who, target);
				}
			}
		});
		Fx.playTo(player, "block.amethyst_block.chime", 1.0f, 1.2f);
	}

	private ItemStack entry(ServerPlayer viewer, Waystone w, boolean here) {
		ItemStack item = new ItemStack(w.icon);
		item.set(DataComponents.ITEM_NAME, Component.literal(w.name).withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
		List<Component> lore = new ArrayList<>();
		lore.add(Text.line("Owner: " + w.ownerName, ChatFormatting.GRAY));
		lore.add(Text.line(pretty(w.dimension) + "  " + w.pos.getX() + " " + w.pos.getY() + " " + w.pos.getZ(),
			ChatFormatting.DARK_GRAY));
		if (viewer.level().dimension() == w.dimension) {
			lore.add(Text.line(Math.round(Math.sqrt(viewer.distanceToSqr(Vec3.atCenterOf(w.pos)))) + " blocks away",
				ChatFormatting.DARK_GRAY));
		}
		lore.add(Component.empty());
		lore.add(here ? Text.line("You are here", ChatFormatting.YELLOW) : Text.line("Click to travel", ChatFormatting.GREEN));
		item.set(DataComponents.LORE, new ItemLore(lore));
		if (here) {
			item.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}
		return item;
	}

	private static String pretty(net.minecraft.resources.ResourceKey<Level> dimension) {
		if (dimension == Level.OVERWORLD) {
			return "Overworld";
		}
		if (dimension == Level.NETHER) {
			return "Nether";
		}
		if (dimension == Level.END) {
			return "The End";
		}
		return Text.pretty(dimension.identifier().getPath());
	}

	private static ItemStack button(net.minecraft.world.item.Item material, String label) {
		ItemStack item = new ItemStack(material);
		item.set(DataComponents.ITEM_NAME, Component.literal(label).withStyle(ChatFormatting.YELLOW));
		return item;
	}

	// =====================================================================
	// Travel
	// =====================================================================

	private boolean inBossFight(ServerPlayer player) {
		for (Boss boss : bosses.bosses().active()) {
			if (boss.engaged().containsKey(player.getUUID())) {
				return true;
			}
		}
		return false;
	}

	public void travel(ServerPlayer player, Waystone target) {
		BossSettings f = f();
		UUID id = player.getUUID();
		int now = Ticks.now();
		int ready = cooldownUntil.getOrDefault(id, 0);
		if (now < ready) {
			player.sendSystemMessage(f.messages.prefixed("waystone-cooldown",
				Map.of("count", Component.literal(String.valueOf((ready - now + 19) / 20)))));
			return;
		}
		if (f.waystoneBlockInCombat && inBossFight(player)) {
			player.sendSystemMessage(f.messages.prefixed("waystone-combat", Map.of()));
			return;
		}
		MinecraftServer server = player.level().getServer();
		ServerLevel world = target.level(server);
		if (world == null || (!f.waystoneCrossDimension && world != player.level())) {
			player.sendSystemMessage(f.messages.prefixed("waystone-blocked", Map.of()));
			return;
		}
		player.sendSystemMessage(f.messages.prefixed("waystone-warmup", name(target.name)));
		Vec3 start = player.position();
		ServerLevel startLevel = player.level();
		int warmup = (int) Math.round(f.waystoneWarmupSeconds * 20);
		int stamp = now;
		travelling.put(id, stamp);
		int[] ticks = {0};
		Scheduler.repeat(1, 2, () -> {
			Integer mine = travelling.get(id);
			if (mine == null || mine != stamp) {
				return false; // cancelled, or a newer warm-up replaced this one
			}
			ServerPlayer p = server.getPlayerList().getPlayer(id);
			if (p == null || !p.isAlive() || p.level() != startLevel || p.position().distanceToSqr(start) > 0.6) {
				if (p != null) {
					p.sendSystemMessage(f().messages.prefixed("waystone-cancelled", Map.of()));
				}
				travelling.remove(id);
				return false;
			}
			double angle = ticks[0] * 0.6;
			Vec3 c = p.position();
			for (int i = 0; i < 3; i++) {
				double a = angle + i * Math.PI * 2 / 3;
				Fx.particle(p.level(), ParticleTypes.PORTAL, c.add(Math.cos(a) * 0.8,
					0.2 + ticks[0] / (double) Math.max(1, warmup) * 1.8, Math.sin(a) * 0.8), 3, 0.05, 0.1);
			}
			if (ticks[0] % 10 == 0) {
				Fx.playTo(p, "block.portal.ambient", 0.25f, 1.6f);
			}
			if ((ticks[0] += 2) < warmup) {
				return true;
			}
			travelling.remove(id);
			if (f().waystoneBlockInCombat && inBossFight(p)) {
				p.sendSystemMessage(f().messages.prefixed("waystone-combat", Map.of())); // a boss started hunting them
				return false;
			}
			arrive(p, target);
			return false;
		});
	}

	/** Called when a travelling player takes damage. */
	public void cancelTravel(UUID player, boolean tell) {
		if (travelling.remove(player) != null && tell) {
			MinecraftServer server = Ticks.server();
			ServerPlayer p = server == null ? null : server.getPlayerList().getPlayer(player);
			if (p != null) {
				p.sendSystemMessage(f().messages.prefixed("waystone-cancelled", Map.of()));
			}
		}
	}

	public boolean isTravelling(UUID player) {
		return travelling.containsKey(player);
	}

	private void arrive(ServerPlayer player, Waystone target) {
		ServerLevel world = target.level(player.level().getServer());
		if (world == null) {
			player.sendSystemMessage(f().messages.prefixed("waystone-blocked", Map.of()));
			return;
		}
		world.getChunk(target.pos); // loads it
		if (!world.getBlockState(target.pos).is(ModItems.WAYSTONE_BLOCK)) {
			remove(target);
			player.sendSystemMessage(f().messages.prefixed("waystone-blocked", Map.of()));
			return;
		}
		Vec3 dest = null;
		BlockPos feet = target.pos.above();
		if (SafeSpots.open(world, feet) && SafeSpots.open(world, feet.above())) {
			dest = Vec3.atBottomCenterOf(feet);
		}
		if (dest == null) {
			dest = SafeSpots.around(world, Vec3.atBottomCenterOf(target.pos), 1, 3, 12);
		}
		if (dest == null) {
			player.sendSystemMessage(f().messages.prefixed("waystone-blocked", Map.of()));
			return;
		}
		Vec3 from = player.position();
		Fx.particle(player.level(), ParticleTypes.REVERSE_PORTAL, from.add(0, 1, 0), 40, 0.4, 0.1);
		Fx.play(player.level(), from, "entity.enderman.teleport", 0.8f, 1.2f);
		if (!player.teleportTo(world, dest.x, dest.y, dest.z, Set.of(), player.getYRot(), player.getXRot(), false)) {
			player.sendSystemMessage(f().messages.prefixed("waystone-blocked", Map.of()));
			return;
		}
		cooldownUntil.put(player.getUUID(), Ticks.now() + (int) Math.round(f().waystoneCooldownSeconds * 20));
		Fx.particle(world, ParticleTypes.REVERSE_PORTAL, dest.add(0, 1, 0), 40, 0.4, 0.1);
		Fx.play(world, dest, "block.respawn_anchor.set_spawn", 0.8f, 1.4f);
		player.sendSystemMessage(f().messages.prefixed("waystone-arrived", name(target.name)));
	}

	public void forget(UUID player) {
		travelling.remove(player);
		cooldownUntil.remove(player);
	}

	/** Server stopping: floating names go (they're never saved anyway). */
	public void shutdown() {
		MinecraftServer server = Ticks.server();
		for (Waystone w : byId.values()) {
			UUID id = labels.get(w.id);
			ServerLevel level = server == null ? null : w.level(server);
			Entity e = id == null || level == null ? null : level.getEntity(id);
			if (e != null) {
				e.discard();
			}
		}
		labels.clear();
		travelling.clear();
	}
}
