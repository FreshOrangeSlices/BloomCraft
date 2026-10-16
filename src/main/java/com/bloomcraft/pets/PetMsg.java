package com.bloomcraft.pets;

import com.bloomcraft.core.Fx;
import com.bloomcraft.core.Text;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;

/** Pet messages. Everyday ones go above the hotbar so chat stays clean; only big moments use chat. */
public final class PetMsg {

	public static final int PINK = 0xF5A9D0;
	public static final int LOVE = 0xF06292;

	private PetMsg() {
	}

	public static MutableComponent text(String text, int rgb) {
		return Text.colored(text, rgb);
	}

	public static MutableComponent text(String text, ChatFormatting color) {
		return Component.literal(text).withStyle(color);
	}

	public static void bar(ServerPlayer player, String text, ChatFormatting color) {
		bar(player, text(text, color));
	}

	public static void bar(ServerPlayer player, String text, int rgb) {
		bar(player, text(text, rgb));
	}

	public static void bar(ServerPlayer player, Component message) {
		Fx.actionBar(player, message);
	}

	public static void chat(ServerPlayer player, String text, ChatFormatting color) {
		chat(player, text(text, color));
	}

	public static void chat(ServerPlayer player, Component message) {
		player.sendSystemMessage(Component.empty().append(text("✿ ", PINK)).append(message));
	}

	public static void error(ServerPlayer player, String text) {
		bar(player, text, ChatFormatting.RED);
	}
}
