package com.bloomcraft.skills;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
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

import java.util.Arrays;

/** /skills: your skill levels. Admins: /skills set and /skills xp. */
public final class SkillsCommand {

	private static final SuggestionProvider<CommandSourceStack> SKILLS = (c, b) ->
		SharedSuggestionProvider.suggest(Arrays.stream(Skill.values()).map(Skill::id), b);

	private SkillsCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher, Skills skills) {
		dispatcher.register(Commands.literal("skills")
			.executes(c -> show(c.getSource(), skills, c.getSource().getPlayerOrException()))
			.then(Commands.literal("reload").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.executes(c -> {
					skills.load();
					c.getSource().sendSuccess(() -> Component.literal("Reloaded skills.yml."), true);
					return 1;
				}))
			.then(Commands.literal("set").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("skill", StringArgumentType.word()).suggests(SKILLS)
						.then(Commands.argument("level", IntegerArgumentType.integer(0, 1000))
							.executes(c -> set(c, skills))))))
			.then(Commands.literal("xp").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.argument("player", EntityArgument.player())
					.then(Commands.argument("skill", StringArgumentType.word()).suggests(SKILLS)
						.then(Commands.argument("amount", DoubleArgumentType.doubleArg(0))
							.executes(c -> xp(c, skills)))))));
	}

	private static int show(CommandSourceStack source, Skills skills, ServerPlayer player) {
		source.sendSystemMessage(Component.literal("Your skills").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
		for (Skill skill : Skill.values()) {
			if (!skills.settings().enabled(skill)) {
				continue;
			}
			source.sendSystemMessage(Component.literal("  ").append(skills.line(player, skill)));
			source.sendSystemMessage(Component.literal("    " + skill.description()).withStyle(ChatFormatting.DARK_GRAY));
		}
		return 1;
	}

	private static Skill skill(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
		Skill skill = Skill.parse(StringArgumentType.getString(c, "skill"));
		if (skill == null) {
			throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
				Component.literal("Unknown skill.")).create();
		}
		return skill;
	}

	private static int set(CommandContext<CommandSourceStack> c, Skills skills) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		Skill skill = skill(c);
		skills.setLevel(player, skill, IntegerArgumentType.getInteger(c, "level"));
		int now = skills.level(player, skill);
		c.getSource().sendSuccess(() -> Component.literal(player.getName().getString() + "'s " + skill.displayName()
			+ " is now level " + now + "."), true);
		return now;
	}

	private static int xp(CommandContext<CommandSourceStack> c, Skills skills) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(c, "player");
		Skill skill = skill(c);
		double amount = DoubleArgumentType.getDouble(c, "amount");
		skills.addXp(player, skill, amount);
		c.getSource().sendSuccess(() -> Component.literal("Gave " + player.getName().getString() + " "
			+ Math.round(amount) + " " + skill.displayName() + " XP (now level " + skills.level(player, skill) + ")."), true);
		return 1;
	}
}
