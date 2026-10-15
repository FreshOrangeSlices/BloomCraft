package com.bloomcraft.bosses.command;

import com.bloomcraft.bosses.Boss;
import com.bloomcraft.bosses.BossKeys;
import com.bloomcraft.bosses.BossManager;
import com.bloomcraft.bosses.BossRank;
import com.bloomcraft.bosses.Bosses;
import com.bloomcraft.bosses.config.BossSettings;
import com.bloomcraft.bosses.config.EmpowermentStat;
import com.bloomcraft.bosses.config.MobCategory;
import com.bloomcraft.bosses.item.BossItems;
import com.bloomcraft.bosses.nemesis.NemesisRecord;
import com.bloomcraft.bosses.relic.RelicEffect;
import com.bloomcraft.bosses.relic.RelicManager;
import com.bloomcraft.bosses.reward.GearKind;
import com.bloomcraft.bosses.trait.BossTrait;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Rng;
import com.bloomcraft.core.Text;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** /bosses (also /ab and /boss). */
public final class BossesCommand {

	private BossesCommand() {
	}

	private static final SuggestionProvider<CommandSourceStack> RANKS = (c, b) ->
		SharedSuggestionProvider.suggest(Arrays.stream(BossRank.values()).map(BossRank::id), b);
	private static final SuggestionProvider<CommandSourceStack> MOBS = (c, b) ->
		SharedSuggestionProvider.suggest(Bosses.get().settings().eligibleMobs().stream()
			.map(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).getPath()), b);

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Bosses mod) {
		var root = Commands.literal("bosses")
			.executes(c -> help(c.getSource()))
			.then(Commands.literal("apply").executes(c -> apply(c.getSource())))
			.then(Commands.literal("inspect").executes(c -> inspect(c.getSource())))
			.then(Commands.literal("stats").executes(c -> stats(c.getSource())))
			.then(Commands.literal("list").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> list(c.getSource())))
			.then(Commands.literal("killall").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> info(c.getSource(), "Removed " + mod.bosses().killAll() + " boss(es).")))
			.then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> {
					mod.load();
					return info(c.getSource(), "Reloaded bosses.yml.");
				}))
			.then(Commands.literal("promote").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> promote(c.getSource(), 1))
				.then(Commands.argument("ranks", IntegerArgumentType.integer(1, 5))
					.executes(c -> promote(c.getSource(), IntegerArgumentType.getInteger(c, "ranks")))))
			.then(Commands.literal("escalate").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(c -> {
						ServerPlayer p = EntityArgument.getPlayer(c, "player");
						mod.escalation().trigger(p);
						return info(c.getSource(), "Escalation triggered on " + p.getName().getString() + ".");
					})))
			.then(Commands.literal("spawn").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("mob", StringArgumentType.word()).suggests(MOBS)
					.executes(c -> spawn(c, null, null))
					.then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
						.executes(c -> spawn(c, StringArgumentType.getString(c, "rank"), null))
						.then(Commands.argument("traits", StringArgumentType.greedyString())
							.suggests((c, b) -> SharedSuggestionProvider.suggest(mod.traits().all().stream().map(BossTrait::id), b))
							.executes(c -> spawn(c, StringArgumentType.getString(c, "rank"),
								StringArgumentType.getString(c, "traits")))))))
			.then(give(mod))
			.then(curse(mod))
			.then(nemesis(mod));
		var node = dispatcher.register(root);
		dispatcher.register(Commands.literal("ab").redirect(node));
		dispatcher.register(Commands.literal("boss").redirect(node));
	}

	// =====================================================================

	private static int help(CommandSourceStack source) {
		source.sendSystemMessage(Component.literal("Bosses").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		line(source, "/bosses apply", "use the rune/relic in one hand on the item in the other");
		line(source, "/bosses inspect", "look at a boss, or inspect your held item");
		line(source, "/bosses stats", "your boss-hunting record");
		line(source, "/bosses nemesis", "what is hunting you");
		if (Commands.hasPermission(Commands.LEVEL_GAMEMASTERS).test(source)) {
			line(source, "/bosses spawn <mob> [rank] [traits]", "spawn a boss where you look");
			line(source, "/bosses give <player> gear|rune|relic|catalyst|compass|totem|soul ...", "give items");
			line(source, "/bosses promote [ranks]", "promote the boss you are looking at");
			line(source, "/bosses escalate <player>", "trigger an Escalation");
			line(source, "/bosses curse <player> <curse|clear> [seconds] [every]", "test a curse on someone");
			line(source, "/bosses nemesis list|summon|challenge|clear <player>", "Nemesis tools");
			line(source, "/bosses nemesis create <player> [mob] [rank]", "make a Nemesis (the boss you look at, or a new one)");
			line(source, "/bosses list | killall | reload", "admin tools");
		}
		return 1;
	}

	private static int apply(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Bosses mod = Bosses.get();
		ItemStack main = player.getMainHandItem();
		ItemStack off = player.getOffhandItem();
		boolean consumableInOff = BossItems.isConsumable(off);
		boolean consumableInMain = BossItems.isConsumable(main);
		if (!consumableInOff && !consumableInMain) {
			player.sendSystemMessage(Text.mm("<gray>Hold the equipment in one hand and the Rune, Relic or Catalyst in the other, then run</gray> <white>/bosses apply</white><gray>. You can also pick it up in your inventory and click it onto the item.</gray>"));
			return 0;
		}
		ItemStack consumable = consumableInOff ? off : main;
		ItemStack target = consumableInOff ? main : off;
		Component error = BossItems.validate(consumable, target);
		if (error != null) {
			Fx.actionBar(player, error);
			return 0;
		}
		Component confirm = BossItems.confirmationPrompt(player, consumable, target, consumableInOff ? "cmd-main" : "cmd-off");
		if (confirm != null) {
			Fx.actionBar(player, confirm.copy().append(Component.literal(" (/bosses apply)").withStyle(ChatFormatting.DARK_GRAY)));
			return 0;
		}
		ItemStack updated = target.copy();
		Component message = BossItems.apply(player, consumable, updated);
		consumable.shrink(1);
		player.setItemInHand(consumableInOff ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, updated);
		Fx.actionBar(player, message);
		mod.relics().refresh(player);
		return 1;
	}

	/** The entity the player is looking at, within {@code range} blocks. */
	public static @Nullable Entity lookedAt(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getViewVector(1.0f);
		Vec3 end = eye.add(look.scale(range));
		AABB box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(player, eye, end, box,
			e -> !e.isSpectator() && e.isPickable(), range * range);
		return hit == null ? null : hit.getEntity();
	}

	private static int inspect(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Bosses mod = Bosses.get();
		Boss boss = mod.bosses().get(lookedAt(player, 24));
		if (boss != null) {
			player.sendSystemMessage(Component.empty().append(boss.name()));
			info(source, "Health: " + (int) Math.ceil(boss.health()) + "/" + Math.round(boss.maxHealth())
				+ "   Damage: x" + Text.num(boss.damageMultiplier()));
			if (boss.traits().isEmpty()) {
				info(source, "No traits.");
			}
			for (BossTrait t : boss.traits()) {
				player.sendSystemMessage(Component.literal(" • " + t.displayName() + " ").withStyle(ChatFormatting.YELLOW)
					.append(Component.literal("(" + t.category().displayName() + ") ").withStyle(ChatFormatting.DARK_GRAY))
					.append(Component.literal(t.description()).withStyle(ChatFormatting.GRAY)));
			}
			return 1;
		}
		ItemStack held = player.getMainHandItem();
		if (held.isEmpty()) {
			return error(source, "Hold an item or look at a boss.");
		}
		player.sendSystemMessage(Component.empty().append(held.getHoverName()));
		BossItems.Kind kind = BossItems.kind(held);
		if (kind == BossItems.Kind.RUNE || kind == BossItems.Kind.RELIC || kind == BossItems.Kind.CATALYST) {
			return info(source, "This is a " + Text.pretty(kind.name()) + ". Click it onto equipment, or use /bosses apply.");
		}
		if (kind == BossItems.Kind.SOUL) {
			return info(source, "A Boss Soul. Right-click a boss with it, or throw it at one, to make the boss rise.");
		}
		List<Component> lore = BossItems.buildLore(held);
		if (lore.isEmpty()) {
			return info(source, "No Empowerments or Relics yet. Relic slots: 0/" + BossItems.relicSlots(held)
				+ "   Empowerments: 0/" + mod.settings().empowermentMaxPerItem);
		}
		lore.forEach(player::sendSystemMessage);
		return info(source, "Empowerments: " + BossItems.list(held, "empowerments").size() + "/"
			+ mod.settings().empowermentMaxPerItem);
	}

	private static int stats(CommandSourceStack source) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		BossSettings s = Bosses.get().settings();
		CompoundTag data = Data.of(player);
		player.sendSystemMessage(Component.literal("Your boss-hunting record").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		int total = 0;
		for (BossRank rank : BossRank.values()) {
			int kills = data.getIntOr(BossKeys.kills(rank), 0);
			total += kills;
			player.sendSystemMessage(Text.colored(" " + s.rank(rank).title() + ": ", rank.rgb())
				.append(Component.literal(String.valueOf(kills)).withStyle(ChatFormatting.WHITE)));
		}
		return info(source, "Total: " + total + "   Relics bound: " + data.getIntOr(BossKeys.RELICS_BOUND, 0));
	}

	private static int list(CommandSourceStack source) {
		Bosses mod = Bosses.get();
		var active = mod.bosses().active();
		info(source, active.size() + " active boss(es) (max " + mod.settings().maxActive + "):");
		for (Boss boss : active) {
			Vec3 l = boss.entity().position();
			source.sendSystemMessage(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY).append(boss.name())
				.append(Component.literal("  " + boss.level().dimension().identifier().getPath() + " " + (int) l.x + " "
					+ (int) l.y + " " + (int) l.z + "  ❤" + (int) Math.ceil(boss.health())).withStyle(ChatFormatting.GRAY)));
		}
		return active.size();
	}

	private static int promote(CommandSourceStack source, int steps) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Boss boss = Bosses.get().bosses().get(lookedAt(player, 24));
		if (boss == null) {
			return error(source, "Look at a boss first.");
		}
		Boss promoted = Bosses.get().bosses().promote(boss, steps, player);
		if (promoted == null) {
			return error(source, "That boss can't be promoted (Nemesis, or already Ascendant).");
		}
		source.sendSystemMessage(Component.literal("Promoted to ").withStyle(ChatFormatting.GRAY).append(promoted.name()));
		return 1;
	}

	private static int spawn(CommandContext<CommandSourceStack> c, @Nullable String rankArg, @Nullable String traitArg)
		throws CommandSyntaxException {
		CommandSourceStack source = c.getSource();
		ServerPlayer player = source.getPlayerOrException();
		Bosses mod = Bosses.get();
		String mobArg = StringArgumentType.getString(c, "mob");
		EntityType<?> type = BossSettings.parseMob(mobArg);
		if (type == null) {
			return error(source, "Not a mob: " + mobArg);
		}
		MobCategory category = mod.settings().categoryFor(type);
		BossRank rank;
		if (rankArg != null && !rankArg.equalsIgnoreCase("random")) {
			rank = BossRank.parse(rankArg);
			if (rank == null) {
				return error(source, "Unknown rank: " + rankArg + " (gray, green, red, purple, gold, ascendant)");
			}
		} else {
			rank = category != null ? category.rollRank(false) : BossRank.values()[Rng.between(0, 4)];
		}
		List<BossTrait> forced = null;
		if (traitArg != null) {
			forced = new ArrayList<>();
			if (!traitArg.equalsIgnoreCase("none")) {
				for (String id : traitArg.replace(' ', ',').split(",")) {
					if (id.isBlank()) {
						continue;
					}
					BossTrait t = mod.traits().get(id.trim());
					if (t == null) {
						return error(source, "Unknown trait: " + id);
					}
					forced.add(t);
				}
			}
		}
		Vec3 look = player.getLookAngle();
		Vec3 flat = new Vec3(look.x, 0, look.z);
		Vec3 at = player.position().add(flat.lengthSqr() < 0.01 ? Vec3.ZERO : flat.normalize().scale(3));
		Boss boss = mod.bosses().summon(type, (ServerLevel) player.level(), at, rank, forced, true,
			category != null ? category.id() : "custom");
		if (boss == null) {
			return error(source, "Couldn't spawn that here (not a mob, or peaceful difficulty).");
		}
		BossManager.markCreated(boss, "command");
		source.sendSystemMessage(Component.literal("Spawned ").withStyle(ChatFormatting.GRAY).append(boss.name()));
		return 1;
	}

	// =====================================================================
	// nemesis
	// =====================================================================

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> nemesis(Bosses mod) {
		return Commands.literal("nemesis")
			.executes(c -> myNemeses(c.getSource(), mod))
			.then(Commands.literal("list").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> listNemeses(c.getSource(), mod, null))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(c -> listNemeses(c.getSource(), mod, EntityArgument.getPlayer(c, "player")))))
			.then(Commands.literal("summon").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player()).executes(c -> {
					ServerPlayer p = EntityArgument.getPlayer(c, "player");
					return info(c.getSource(), "Called " + mod.nemesis().summonNow(p.getUUID()) + " Nemes(es) to "
						+ p.getName().getString() + ".");
				})))
			.then(Commands.literal("challenge").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player()).executes(c -> {
					ServerPlayer p = EntityArgument.getPlayer(c, "player");
					int n = mod.nemesis().reissueChallenges(p);
					return info(c.getSource(), "Gave " + p.getName().getString() + " " + n + " Challenge" + (n == 1 ? "" : "s") + ".");
				})))
			.then(Commands.literal("clear").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player()).executes(c -> {
					ServerPlayer p = EntityArgument.getPlayer(c, "player");
					return info(c.getSource(), "Removed " + mod.nemesis().clear(p.getUUID()) + " Nemes(es) of "
						+ p.getName().getString() + ".");
				})))
			.then(Commands.literal("create").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.executes(c -> createNemesis(c, mod, null, null))
					.then(Commands.argument("mob", StringArgumentType.word()).suggests(MOBS)
						.executes(c -> createNemesis(c, mod, StringArgumentType.getString(c, "mob"), null))
						.then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
							.executes(c -> createNemesis(c, mod, StringArgumentType.getString(c, "mob"),
								StringArgumentType.getString(c, "rank")))))));
	}

	private static int myNemeses(CommandSourceStack source, Bosses mod) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		List<NemesisRecord> mine = mod.nemesis().forOwner(player.getUUID());
		if (mine.isEmpty()) {
			return info(source, "Nothing is hunting you... yet.");
		}
		player.sendSystemMessage(Component.literal("Your Nemeses").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
		for (NemesisRecord r : mine) {
			player.sendSystemMessage(Component.literal(" ").append(mod.nemesis().displayName(r)));
			info(source, "   killed you " + r.kills + "x, you fled " + r.escapes + "x, " + r.traits.size()
				+ " traits - " + mod.nemesis().describeReturn(r));
		}
		return mine.size();
	}

	private static int listNemeses(CommandSourceStack source, Bosses mod, @Nullable ServerPlayer of) {
		List<NemesisRecord> all = of == null ? mod.nemesis().all() : mod.nemesis().forOwner(of.getUUID());
		info(source, all.size() + " Nemes" + (all.size() == 1 ? "is" : "es") + ":");
		for (NemesisRecord r : all) {
			source.sendSystemMessage(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
				.append(mod.nemesis().displayName(r))
				.append(Component.literal("  hunts " + r.ownerName + ", " + mod.nemesis().describeReturn(r))
					.withStyle(ChatFormatting.GRAY)));
			StringBuilder adapted = new StringBuilder();
			r.adapted.forEach((kind, level) -> adapted.append(adapted.isEmpty() ? "" : ", ")
				.append(kind.plain()).append(level >= 2 ? " (immune)" : " (resists)"));
			source.sendSystemMessage(Component.literal("     adapted to: " + (adapted.isEmpty() ? "nothing yet" : adapted)
				+ "; surrender: " + r.surrender.plain()).withStyle(ChatFormatting.DARK_GRAY));
		}
		return all.size();
	}

	/**
	 * /bosses nemesis create <player> [mob] [rank]: makes the boss you look at that player's Nemesis, or (with a mob)
	 * spawns a fresh boss near them and makes it theirs. It comes for them straight away.
	 */
	private static int createNemesis(CommandContext<CommandSourceStack> c, Bosses mod, @Nullable String mob,
									 @Nullable String rankArg) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(c, "player");
		CommandSourceStack source = c.getSource();
		Boss boss;
		if (mob == null) {
			boss = source.getEntity() instanceof ServerPlayer looker ? mod.bosses().get(lookedAt(looker, 24)) : null;
			if (boss == null) {
				return error(source, "Look at a boss, or name a mob: /bosses nemesis create <player> <mob> [rank]");
			}
		} else {
			EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(
				net.minecraft.resources.Identifier.tryParse(mob.contains(":") ? mob : "minecraft:" + mob)).orElse(null);
			BossRank rank = rankArg == null ? BossRank.RED : BossRank.parse(rankArg);
			if (type == null || rank == null) {
				return error(source, "Unknown mob or rank.");
			}
			Vec3 at = com.bloomcraft.core.SafeSpots.around(target.level(), target.position(), 12, 18, 16);
			if (at == null) {
				return error(source, "No room around " + target.getName().getString() + ".");
			}
			boss = mod.bosses().summon(type, target.level(), at, rank, null, false, "nemesis");
			if (boss == null) {
				return error(source, "That mob can't be a boss here.");
			}
			com.bloomcraft.bosses.BossManager.markCreated(boss, "command");
		}
		if (boss.isNemesis()) {
			return error(source, "That boss is already a Nemesis.");
		}
		NemesisRecord r = mod.nemesis().forceCreate(boss, target);
		if (r == null) {
			return error(source, "Couldn't make a Nemesis.");
		}
		mod.bosses().engage(boss, target);
		source.sendSystemMessage(Component.literal("Created ").withStyle(ChatFormatting.GRAY)
			.append(mod.nemesis().displayName(r)).append(Component.literal(" for " + target.getName().getString() + ".")
				.withStyle(ChatFormatting.GRAY)));
		return 1;
	}

	// =====================================================================
	// curse (testing curses on a player)
	// =====================================================================

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> curse(Bosses mod) {
		SuggestionProvider<CommandSourceStack> curses = (c, b) -> {
			List<String> ids = new ArrayList<>(List.of("clear"));
			for (RelicEffect e : mod.relics().enabledEffects(true)) {
				ids.add(e.id());
			}
			return SharedSuggestionProvider.suggest(ids, b);
		};
		var curseArg = Commands.argument("curse", StringArgumentType.word()).suggests(curses)
			.executes(c -> curse(c, mod, null, null))
			.then(Commands.argument("seconds", IntegerArgumentType.integer(1, 3600))
				.executes(c -> curse(c, mod, IntegerArgumentType.getInteger(c, "seconds"), null))
				.then(Commands.argument("every", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0.5, 600))
					.executes(c -> curse(c, mod, IntegerArgumentType.getInteger(c, "seconds"),
						com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(c, "every")))));
		return Commands.literal("curse").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
			.then(Commands.argument("player", EntityArgument.player()).then(curseArg));
	}

	/**
	 * /bosses curse <player> <curse> [seconds] [every]: no seconds fires the curse's moment once (or gives an
	 * always-on curse for 30s); seconds gives the curse for that long; every also fires it on that rhythm.
	 */
	private static int curse(CommandContext<CommandSourceStack> c, Bosses mod, @Nullable Integer seconds,
							 @Nullable Double every) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(c, "player");
		String arg = StringArgumentType.getString(c, "curse");
		String who = target.getName().getString();
		if (arg.equalsIgnoreCase("clear")) {
			return info(c.getSource(), "Ended " + mod.relics().clearTests(target) + " curse test(s) on " + who + ".");
		}
		RelicEffect curse = mod.relics().get(arg);
		if (curse == null || !curse.curse()) {
			return error(c.getSource(), "Unknown curse: " + arg);
		}
		boolean fires = RelicManager.hasTrigger(curse);
		if (seconds == null) {
			if (!fires) {
				mod.relics().addTest(target, curse, 30);
				return info(c.getSource(), curse.displayName() + " is always on rather than a single moment, so " + who
					+ " has it for 30 seconds.");
			}
			if (curse.trigger(target, mod.relics())) {
				return info(c.getSource(), curse.displayName() + " triggered once on " + who + ".");
			}
			return error(c.getSource(), curse.displayName() + " couldn't happen right now (already going, or no room around "
				+ who + "). Try again in a moment.");
		}
		mod.relics().addTest(target, curse, seconds);
		if (every == null || !fires) {
			return info(c.getSource(), who + " has " + curse.displayName() + " for " + seconds + "s"
				+ (every != null ? " (it's always on, so there's nothing to repeat)." : ", at its normal odds."));
		}
		mod.relics().repeatTest(target, curse, seconds, (int) Math.round(every * 20));
		return info(c.getSource(), who + " has " + curse.displayName() + " for " + seconds + "s, firing every "
			+ Text.num(every) + "s.");
	}

	// =====================================================================
	// give
	// =====================================================================

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> give(Bosses mod) {
		var player = Commands.argument("player", EntityArgument.player());
		player.then(item("gear", c -> mod.rewards().gear().create(BossRank.GOLD, null, "an admin", null)).then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
			.executes(c -> giveItem(c, mod.rewards().gear().create(rankArg(c, "rank", BossRank.GOLD), null, "an admin", null)))
			.then(Commands.argument("kind", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(Arrays.stream(GearKind.values()).map(k -> k.name().toLowerCase(Locale.ROOT)), b))
				.executes(c -> giveItem(c, mod.rewards().gear().create(rankArg(c, "rank", BossRank.GOLD), null, "an admin",
					GearKind.parse(StringArgumentType.getString(c, "kind"))))))));
		player.then(item("rune", c -> BossItems.randomRune(BossRank.GOLD))
			.then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
				.executes(c -> giveItem(c, BossItems.randomRune(rankArg(c, "rank", BossRank.GOLD))))
				.then(Commands.argument("stat", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(mod.settings().empowermentStats.stream().map(EmpowermentStat::id), b))
					.executes(c -> {
						EmpowermentStat stat = mod.settings().stat(StringArgumentType.getString(c, "stat"));
						BossRank rank = rankArg(c, "rank", BossRank.GOLD);
						return stat == null ? error(c.getSource(), "Unknown stat.")
							: giveItem(c, BossItems.rune(rank, stat, stat.roll(rank, mod.settings().fixedRuneValues)));
					}))));
		player.then(item("relic", c -> BossItems.randomRelic())
			.then(Commands.argument("relic", StringArgumentType.word())
				.suggests((c, b) -> SharedSuggestionProvider.suggest(relicIds(mod, false, true), b))
				.executes(c -> giveRelic(c, mod, StringArgumentType.getString(c, "relic"), "random"))
				.then(Commands.argument("curse", StringArgumentType.word())
					.suggests((c, b) -> SharedSuggestionProvider.suggest(relicIds(mod, true, true), b))
					.executes(c -> giveRelic(c, mod, StringArgumentType.getString(c, "relic"), StringArgumentType.getString(c, "curse"))))));
		player.then(item("catalyst", c -> BossItems.catalyst()));
		player.then(item("compass", c -> BossItems.compass(1))
			.then(Commands.argument("tier", IntegerArgumentType.integer(1, 5))
				.executes(c -> giveItem(c, BossItems.compass(IntegerArgumentType.getInteger(c, "tier"))))));
		player.then(item("totem", c -> BossItems.totem(BossRank.GREEN))
			.then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
				.executes(c -> {
					BossRank rank = rankArg(c, "rank", BossRank.GREEN);
					return giveItem(c, BossItems.totem(rank.atLeast(BossRank.GOLD) ? BossRank.GOLD : rank));
				})));
		player.then(item("soul", c -> BossItems.soul(BossRank.GREEN, 1))
			.then(Commands.argument("rank", StringArgumentType.word()).suggests(RANKS)
				.executes(c -> giveItem(c, BossItems.soul(rankArg(c, "rank", BossRank.GREEN), 1)))
				.then(Commands.argument("amount", IntegerArgumentType.integer(1, 64))
					.executes(c -> giveItem(c, BossItems.soul(rankArg(c, "rank", BossRank.GREEN),
						IntegerArgumentType.getInteger(c, "amount")))))));
		return Commands.literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).then(player);
	}

	private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> item(String name,
		Function<CommandContext<CommandSourceStack>, @Nullable ItemStack> make) {
		return Commands.literal(name).executes(c -> giveItem(c, make.apply(c)));
	}

	private static List<String> relicIds(Bosses mod, boolean curses, boolean withRandom) {
		List<String> out = new ArrayList<>();
		if (withRandom) {
			out.add("random");
		}
		if (curses) {
			out.add("none");
		}
		for (RelicEffect e : mod.relics().enabledEffects(curses)) {
			out.add(e.id());
		}
		return out;
	}

	private static int giveRelic(CommandContext<CommandSourceStack> c, Bosses mod, String relicArg, String curseArg)
		throws CommandSyntaxException {
		RelicEffect effect = relicArg.equalsIgnoreCase("random") ? mod.relics().rollEffect() : mod.relics().get(relicArg);
		if (effect == null || effect.curse()) {
			return error(c.getSource(), "Unknown relic: " + relicArg);
		}
		RelicEffect curse = switch (curseArg.toLowerCase(Locale.ROOT)) {
			case "none" -> null;
			case "random" -> Rng.chance(mod.settings().corruptionChance) ? mod.relics().rollCurse() : null;
			default -> mod.relics().get(curseArg);
		};
		return giveItem(c, BossItems.relic(effect, curse));
	}

	private static BossRank rankArg(CommandContext<CommandSourceStack> c, String name, BossRank def) {
		try {
			BossRank rank = BossRank.parse(StringArgumentType.getString(c, name));
			return rank == null ? def : rank;
		} catch (IllegalArgumentException ex) {
			return def;
		}
	}

	private static int giveItem(CommandContext<CommandSourceStack> c, @Nullable ItemStack item) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(c, "player");
		if (item == null || item.isEmpty()) {
			return error(c.getSource(), "Nothing to give (is that feature turned on?).");
		}
		MutableComponent name = item.getHoverName().copy();
		if (!target.getInventory().add(item)) {
			target.spawnAtLocation(target.level(), item);
		}
		c.getSource().sendSystemMessage(Component.literal("Gave ").withStyle(ChatFormatting.GRAY).append(name)
			.append(Component.literal(" to " + target.getName().getString() + ".").withStyle(ChatFormatting.GRAY)));
		return 1;
	}

	// =====================================================================

	private static int info(CommandSourceStack source, String text) {
		source.sendSystemMessage(Component.literal(text).withStyle(ChatFormatting.GRAY));
		return 1;
	}

	private static int error(CommandSourceStack source, String text) {
		source.sendFailure(Component.literal(text));
		return 0;
	}

	private static void line(CommandSourceStack source, String command, String description) {
		source.sendSystemMessage(Component.literal(command).withStyle(ChatFormatting.YELLOW)
			.append(Component.literal(" - " + description).withStyle(ChatFormatting.GRAY)));
	}
}
