package com.bloomcraft.skills;

import com.bloomcraft.core.ConfigFiles;
import com.bloomcraft.core.Data;
import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Text;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Player skills: a level (0 to the max, 100 by default) and the XP towards the next one, per skill. Kept on the
 * player (it survives death). Other parts of the mod add XP and read levels; this class only keeps count and
 * says when someone levels up.
 */
public final class Skills {

	/** Something that wants to hear about level-ups (to announce what just unlocked). */
	public interface LevelUpListener {
		void levelUp(ServerPlayer player, Skill skill, int from, int to);
	}

	private static final String ROOT = "skills";

	private static @Nullable Skills instance;

	private SkillSettings settings;
	private final List<LevelUpListener> listeners = new ArrayList<>();

	private Skills() {
		settings = new SkillSettings(ConfigFiles.load("skills.yml"));
	}

	public static Skills get() {
		return instance;
	}

	public static void init() {
		instance = new Skills();
		CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) ->
			SkillsCommand.register(dispatcher, instance));
	}

	public void starting(MinecraftServer server) {
		load();
	}

	/** (Re)reads config/bloomcraft/skills.yml. */
	public void load() {
		settings = new SkillSettings(ConfigFiles.load("skills.yml"));
	}

	public SkillSettings settings() {
		return settings;
	}

	public void onLevelUp(LevelUpListener listener) {
		listeners.add(listener);
	}

	private static CompoundTag tag(Player player, Skill skill) {
		CompoundTag data = Data.peek(player);
		return data == null ? new CompoundTag() : data.getCompoundOrEmpty(ROOT).getCompoundOrEmpty(skill.id());
	}

	private static void store(Player player, Skill skill, int level, double xp) {
		CompoundTag all = Data.of(player).getCompoundOrEmpty(ROOT);
		CompoundTag one = new CompoundTag();
		one.putInt("level", level);
		one.putDouble("xp", xp);
		all.put(skill.id(), one);
		Data.of(player).put(ROOT, all);
	}

	public int level(Player player, Skill skill) {
		return Math.min(settings.maxLevel, Math.max(0, tag(player, skill).getIntOr("level", 0)));
	}

	/** XP towards the next level. */
	public double xp(Player player, Skill skill) {
		return Math.max(0, tag(player, skill).getDoubleOr("xp", 0));
	}

	public double xpToNext(int level) {
		return settings.xpToNext(level);
	}

	public boolean maxed(Player player, Skill skill) {
		return level(player, skill) >= settings.maxLevel;
	}

	/** Adds XP (after the config's multipliers), levelling up as often as it fills. */
	public void addXp(ServerPlayer player, Skill skill, double amount) {
		if (amount <= 0 || !settings.enabled(skill) || player.isSpectator()) {
			return;
		}
		int level = level(player, skill);
		if (level >= settings.maxLevel) {
			return;
		}
		double xp = xp(player, skill) + amount * settings.multiplier(skill);
		int before = level;
		while (level < settings.maxLevel && xp >= settings.xpToNext(level)) {
			xp -= settings.xpToNext(level);
			level++;
		}
		if (level >= settings.maxLevel) {
			xp = 0;
		}
		store(player, skill, level, xp);
		if (level > before) {
			leveledUp(player, skill, before, level);
		}
	}

	/** Sets the level outright (XP towards the next one starts over). For admins and tests. */
	public void setLevel(ServerPlayer player, Skill skill, int level) {
		int before = level(player, skill);
		int to = Math.max(0, Math.min(settings.maxLevel, level));
		store(player, skill, to, 0);
		if (to > before) {
			leveledUp(player, skill, before, to);
		}
	}

	private void leveledUp(ServerPlayer player, Skill skill, int from, int to) {
		Fx.actionBar(player, Text.colored("✦ " + skill.displayName() + " " + to + " ✦", skill.color())
			.withStyle(ChatFormatting.BOLD));
		Fx.playTo(player, "entity.player.levelup", 0.5f, 1.6f);
		if (to / 5 > from / 5 || to >= settings.maxLevel) {
			player.sendSystemMessage(Component.empty()
				.append(Text.colored(skill.displayName(), skill.color()))
				.append(Component.literal(" reached level " + to + (to >= settings.maxLevel ? " (max)!" : "!"))
					.withStyle(ChatFormatting.GRAY)));
		}
		for (LevelUpListener listener : listeners) {
			listener.levelUp(player, skill, from, to);
		}
	}

	/** "Taming 12  ■■■■□□□□□□ 340/680" */
	public Component line(Player player, Skill skill) {
		int level = level(player, skill);
		Component name = Text.colored(skill.displayName() + " " + level, skill.color());
		if (level >= settings.maxLevel) {
			return Component.empty().append(name).append(Component.literal("  max level").withStyle(ChatFormatting.LIGHT_PURPLE));
		}
		double need = settings.xpToNext(level);
		double have = xp(player, skill);
		int filled = (int) Math.max(0, Math.min(10, Math.round(10 * have / need)));
		return Component.empty().append(name).append(Component.literal("  "))
			.append(Component.literal("■".repeat(filled)).withStyle(ChatFormatting.GOLD))
			.append(Component.literal("■".repeat(10 - filled)).withStyle(ChatFormatting.DARK_GRAY))
			.append(Component.literal(" " + Math.round(have) + "/" + Math.round(need)).withStyle(ChatFormatting.GRAY));
	}
}
