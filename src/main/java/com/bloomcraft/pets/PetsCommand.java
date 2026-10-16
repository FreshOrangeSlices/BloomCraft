package com.bloomcraft.pets;

import com.bloomcraft.core.Books;
import com.bloomcraft.core.Players;
import com.bloomcraft.core.QuestFlags;
import com.bloomcraft.core.Text;
import com.bloomcraft.skills.Skill;
import com.bloomcraft.skills.Skills;
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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** /pets (also /pet): the menu, plus typed versions of everything the blooms and the toy do. */
public final class PetsCommand {

	private record PendingRelease(List<UUID> petIds, long until) {
	}

	private static final Map<UUID, PendingRelease> RELEASES = new HashMap<>();

	private PetsCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Pets mod) {
		SuggestionProvider<CommandSourceStack> names = (c, b) -> {
			ServerPlayer p = c.getSource().getPlayer();
			return SharedSuggestionProvider.suggest(p == null || !mod.store().ready() ? List.of()
				: mod.store().pets(p.getUUID()).stream().map(pet -> pet.name).toList(), b);
		};
		SuggestionProvider<CommandSourceStack> skilled = (c, b) ->
			SharedSuggestionProvider.suggest(PetSkills.skilled().stream().map(Species::id), b);
		SuggestionProvider<CommandSourceStack> species = (c, b) -> SharedSuggestionProvider.suggest(Species.ids(), b);

		var root = Commands.literal("pets")
			.executes(c -> run(c, p -> mod.menus().openPets(p)))
			.then(Commands.literal("menu").executes(c -> run(c, p -> mod.menus().openPets(p))))
			.then(Commands.literal("help").executes(c -> help(c.getSource())))
			.then(Commands.literal("summon")
				.then(Commands.argument("name", StringArgumentType.greedyString()).suggests(names)
					.executes(c -> run(c, p -> {
						Pet pet = find(mod, p, StringArgumentType.getString(c, "name"));
						if (pet != null) {
							mod.pets().summon(p, pet, true);
						}
					}))))
			.then(Commands.literal("dismiss").executes(c -> run(c, p -> mod.pets().dismiss(p, true))))
			.then(Commands.literal("list").executes(c -> run(c, p -> list(mod, p))))
			.then(Commands.literal("rename")
				.then(Commands.argument("name", StringArgumentType.greedyString())
					.executes(c -> run(c, p -> {
						Pet pet = out(mod, p);
						if (pet != null) {
							mod.pets().rename(p, pet, StringArgumentType.getString(c, "name"));
						}
					}))))
			.then(Commands.literal("bloom")
				.executes(c -> run(c, p -> {
					Pet pet = out(mod, p);
					if (pet != null) {
						mod.menus().giveBloom(p, pet);
					}
				}))
				.then(Commands.argument("name", StringArgumentType.greedyString()).suggests(names)
					.executes(c -> run(c, p -> {
						Pet pet = find(mod, p, StringArgumentType.getString(c, "name"));
						if (pet != null) {
							mod.menus().giveBloom(p, pet);
						}
					}))))
			.then(Commands.literal("toy").executes(c -> run(c, p -> {
				if (Blooms.hasToy(p)) {
					PetMsg.bar(p, "You already have a Pet Toy.", ChatFormatting.GRAY);
				} else if (Blooms.giveToyIfMissing(p, true)) {
					PetMsg.bar(p, "Here's your Pet Toy!", PetMsg.PINK);
				}
			})))
			.then(Commands.literal("fetch").executes(c -> run(c, p -> {
				if (Blooms.hasFetchStick(p)) {
					PetMsg.bar(p, "You already have a Fetch Stick.", ChatFormatting.GRAY);
				} else if (Blooms.mayHandOut(p, "fetch")) {
					Players.give(p, Blooms.fetchStick());
				} else {
					PetMsg.bar(p, "You just got a Fetch Stick. Try again in a minute.", ChatFormatting.GRAY);
				}
			})))
			.then(Commands.literal("guide")
				.executes(c -> run(c, p -> Books.open(p, PetGuide.book())))
				.then(Commands.literal("give").executes(c -> run(c, p -> Players.give(p, PetGuide.book())))))
			.then(Commands.literal("station").executes(c -> run(c, p -> {
				PetManager.Active a = mod.pets().active(p.getUUID());
				if (a == null) {
					PetMsg.chat(p, PetMsg.text("Call out the pet you want to station first.", ChatFormatting.RED));
				} else {
					mod.pets().station(p, a);
				}
			})))
			.then(Commands.literal("taming")
				.executes(c -> run(c, p -> taming(mod, p, null)))
				.then(Commands.argument("animal", StringArgumentType.word()).suggests(skilled)
					.executes(c -> run(c, p -> taming(mod, p, StringArgumentType.getString(c, "animal"))))))
			.then(Commands.literal("release")
				.then(Commands.argument("names", StringArgumentType.greedyString()).suggests(names)
					.executes(c -> run(c, p -> release(mod, p, StringArgumentType.getString(c, "names"))))))
			.then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> {
					mod.load();
					c.getSource().sendSuccess(() -> Component.literal("Reloaded pets.yml."), true);
					return 1;
				}))
			.then(Commands.literal("give").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("species", StringArgumentType.word()).suggests(species)
						.executes(c -> give(mod, c, 1))
						.then(Commands.argument("level", IntegerArgumentType.integer(1, Pet.MAX_LEVEL))
							.executes(c -> give(mod, c, IntegerArgumentType.getInteger(c, "level")))))));
		var node = dispatcher.register(root);
		dispatcher.register(Commands.literal("pet").redirect(node));
	}

	private interface PlayerAction {
		void run(ServerPlayer player) throws CommandSyntaxException;
	}

	private static int run(CommandContext<CommandSourceStack> c, PlayerAction action) throws CommandSyntaxException {
		ServerPlayer p = c.getSource().getPlayerOrException();
		Pets mod = Pets.get();
		if (!mod.enabled() || !mod.store().ready()) {
			p.sendSystemMessage(Component.literal("Pets are turned off in this world (pets.yml).").withStyle(ChatFormatting.RED));
			return 0;
		}
		action.run(p);
		return 1;
	}

	private static @Nullable Pet find(Pets mod, ServerPlayer p, String name) {
		Pet pet = mod.store().byName(p.getUUID(), name);
		if (pet == null) {
			PetMsg.chat(p, PetMsg.text("You don't have a pet called " + name.trim() + ". /pets list shows their names.",
				ChatFormatting.RED));
		}
		return pet;
	}

	private static @Nullable Pet out(Pets mod, ServerPlayer p) {
		PetManager.Active a = mod.pets().active(p.getUUID());
		if (a == null) {
			PetMsg.chat(p, PetMsg.text("None of your pets is out. Call one first, or name one.", ChatFormatting.RED));
			return null;
		}
		return a.pet;
	}

	private static void list(Pets mod, ServerPlayer p) {
		List<Pet> pets = mod.store().pets(p.getUUID());
		if (pets.isEmpty()) {
			PetMsg.chat(p, PetMsg.text("No pets yet. Sneak + feed an animal its favourite food a few times to bond "
				+ "with it.", ChatFormatting.GRAY));
			return;
		}
		PetMsg.chat(p, PetMsg.text("Your pets (" + pets.size() + "/" + mod.settings().maxPets + "):", PetMsg.PINK));
		long now = System.currentTimeMillis();
		for (Pet pet : pets) {
			var line = Component.empty().append(Text.colored("  " + pet.name, pet.species.category().color()))
				.append(PetMsg.text(" · " + pet.species.displayName() + " · level " + pet.level, ChatFormatting.GRAY));
			if (mod.settings().happiness) {
				line.append(PetMsg.text(" · ", ChatFormatting.GRAY)).append(mod.happiness().bar(pet, now));
			}
			if (mod.pets().isOut(pet)) {
				line.append(PetMsg.text(" · out", ChatFormatting.GREEN));
			} else if (pet.stationed()) {
				line.append(PetMsg.text(" · at its post", ChatFormatting.GOLD));
			} else if (pet.resting(now)) {
				line.append(PetMsg.text(" · resting " + ((pet.restingUntil - now + 999) / 1000) + "s", ChatFormatting.RED));
			}
			p.sendSystemMessage(line);
		}
	}

	/** /pets taming [animal]: the combat pets' Taming skills. Without an animal, the one that's out. */
	private static void taming(Pets mod, ServerPlayer p, @Nullable String raw) {
		Species sp = raw == null ? null : Species.parse(raw);
		if (raw == null) {
			PetManager.Active a = mod.pets().active(p.getUUID());
			if (a != null && PetSkills.hasSkills(a.pet.species)) {
				sp = a.pet.species;
			}
		}
		if (sp == null || !PetSkills.hasSkills(sp)) {
			List<String> ids = new ArrayList<>();
			for (Species s : PetSkills.skilled()) {
				ids.add(s.id());
			}
			PetMsg.chat(p, PetMsg.text("Usage: /pets taming <" + String.join(" | ", ids) + ">", ChatFormatting.RED));
			return;
		}
		PetSkills skills = mod.skills();
		int level = Skills.get().level(p, Skill.TAMING);
		p.sendSystemMessage(Component.empty().append(PetMsg.text("-----[ ", ChatFormatting.RED))
			.append(PetMsg.text(sp.displayName() + " · Taming", ChatFormatting.GREEN))
			.append(PetMsg.text(" ]-----", ChatFormatting.RED)));
		if (!skills.on()) {
			p.sendSystemMessage(PetMsg.text("Taming skills for pets are turned off in this world.", ChatFormatting.GRAY));
		} else {
			p.sendSystemMessage(Component.empty().append(PetMsg.text("Your Taming: ", ChatFormatting.GRAY))
				.append(Skills.get().line(p, Skill.TAMING))
				.append(PetMsg.text("  (your skill, not the pet's level)", ChatFormatting.DARK_GRAY)));
		}
		p.sendSystemMessage(PetMsg.text("Skills:", ChatFormatting.GOLD));
		for (Component line : skills.describe(sp, level)) {
			p.sendSystemMessage(line);
		}
		p.sendSystemMessage(PetMsg.text("Its hits give you Taming XP. A sad pet's skills rest until it cheers up.",
			ChatFormatting.DARK_GRAY));
	}

	/** /pets release Mochi, Biscuit, Pebble: several at once, separated by commas; typed twice to confirm. */
	private static void release(Pets mod, ServerPlayer p, String names) {
		List<Pet> pets = new ArrayList<>();
		for (String name : names.split(",")) {
			if (name.isBlank()) {
				continue;
			}
			Pet pet = find(mod, p, name);
			if (pet == null) {
				return;
			}
			if (!pets.contains(pet)) {
				pets.add(pet);
			}
		}
		if (pets.isEmpty()) {
			return;
		}
		List<UUID> ids = pets.stream().map(pet -> pet.id).toList();
		List<String> shown = pets.stream().map(pet -> pet.name).toList();
		long now = System.currentTimeMillis();
		PendingRelease pending = RELEASES.get(p.getUUID());
		if (pending == null || !pending.petIds().equals(ids) || pending.until() < now) {
			RELEASES.put(p.getUUID(), new PendingRelease(ids, now + 15_000));
			PetMsg.chat(p, PetMsg.text("Release " + String.join(", ", shown) + " for good? Anything they carry "
				+ "comes back to you. Type the same command again within 15 seconds to confirm.", ChatFormatting.GOLD));
			return;
		}
		RELEASES.remove(p.getUUID());
		for (Pet pet : pets) {
			mod.pets().release(p, pet);
		}
		PetMsg.chat(p, PetMsg.text(String.join(", ", shown) + " said goodbye. Take care!", ChatFormatting.GRAY));
	}

	/** /pets give <player> <species> [level] */
	private static int give(Pets mod, CommandContext<CommandSourceStack> c, int level) throws CommandSyntaxException {
		ServerPlayer target = EntityArgument.getPlayer(c, "player");
		Species species = Species.parse(StringArgumentType.getString(c, "species"));
		CommandSourceStack source = c.getSource();
		if (!mod.store().ready()) {
			return 0;
		}
		if (species == null) {
			source.sendFailure(Component.literal("Unknown animal. Try one of: " + String.join(", ", Species.ids())));
			return 0;
		}
		PetStore store = mod.store();
		if (store.count(target.getUUID()) >= mod.settings().maxPets) {
			source.sendFailure(Component.literal(target.getName().getString() + " already has the most pets allowed."));
			return 0;
		}
		Pet.RideStyle style = !species.small() ? Pet.RideStyle.NORMAL
			: ThreadLocalRandom.current().nextBoolean() ? Pet.RideStyle.GROW : Pet.RideStyle.SHRINK;
		Pet pet = new Pet(UUID.randomUUID(), target.getUUID(), species, Blooms.freshName(store, target.getUUID()), style);
		pet.level = level;
		pet.happiness = mod.settings().happyStart;
		store.add(pet);
		QuestFlags.mark(target, "pet_adopted");
		if (species.combat()) {
			QuestFlags.mark(target, "pet_combat_owned");
		}
		Blooms.giveToyIfMissing(target, false);
		mod.menus().refresh(target);
		PetMsg.chat(target, PetMsg.text("You got a new pet: " + pet.name + " the " + species.displayName()
			+ " (level " + level + "). Punch with your Pet Toy to see it.", PetMsg.PINK));
		source.sendSuccess(() -> Component.literal("Gave " + target.getName().getString() + " " + pet.name + " the "
			+ species.displayName() + " (level " + level + ")."), true);
		return 1;
	}

	private static int help(CommandSourceStack source) {
		List<String> lines = new ArrayList<>(List.of(
			"/pets  open your pets",
			"/pets summon <name>  /pets dismiss",
			"/pets list  see all your pets",
			"/pets rename <new name>  rename the pet that's out",
			"/pets toy  a new Pet Toy, if you lost yours",
			"/pets bloom [name]  get a pet's bloom back",
			"/pets guide  read the Pet Guide",
			"/pets station  leave the pet that's out at a post (level 8+)",
			"/pets fetch  a Fetch Stick to play with",
			"/pets taming <animal>  a combat pet's Taming skills",
			"/pets release <name>[, <name>...]  say goodbye for good",
			"/skills  your skill levels"));
		if (source.permissions() != null && Commands.LEVEL_GAMEMASTERS.check(source.permissions())) {
			lines.add("/pets give <player> <animal> [level]  /pets reload");
		}
		source.sendSystemMessage(Text.colored("BloomCraft Pets", PetMsg.PINK));
		for (String line : lines) {
			source.sendSystemMessage(Component.literal("  " + line).withStyle(ChatFormatting.GRAY));
		}
		return 1;
	}
}
