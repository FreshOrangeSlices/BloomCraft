package com.bloomcraft.bosses;

import com.bloomcraft.core.Text;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.BossEvent;
import net.minecraft.world.scores.TeamColor;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * The boss ranks. Colour and star count are fixed identity; every balance value lives in the config.
 * The first five appear naturally; Ascendant is only reached by promoting a boss with Souls.
 */
public enum BossRank {

	GRAY(1, 0xB8B8B8, 0x4A4A4A, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS, TeamColor.GRAY, "Hard"),
	GREEN(2, 0x55E655, 0x1D7A1D, BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS, TeamColor.GREEN, "Very Hard"),
	RED(3, 0xFF5050, 0xA01414, BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6, TeamColor.RED, "Brutal"),
	PURPLE(4, 0xC77DFF, 0x6A1B9A, BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10, TeamColor.LIGHT_PURPLE, "Nightmare"),
	GOLD(5, 0xFFC125, 0x9A6A00, BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.NOTCHED_20, TeamColor.GOLD, "Legendary"),
	ASCENDANT(6, 0xEAF4FF, 0x3F5F86, BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.NOTCHED_20, TeamColor.WHITE, "Ascendant");

	/** Pearl-white shimmer used for Ascendant names. */
	public static final int[] ASCENDANT_GRADIENT = {0xFFFFFF, 0xA8DCFF, 0xFFFFFF};

	private final int stars;
	private final int rgb;
	private final int bookRgb;
	private final BossEvent.BossBarColor barColor;
	private final BossEvent.BossBarOverlay barOverlay;
	private final TeamColor teamColor;
	private final String defaultName;

	BossRank(int stars, int rgb, int bookRgb, BossEvent.BossBarColor barColor, BossEvent.BossBarOverlay barOverlay,
			 TeamColor teamColor, String defaultName) {
		this.stars = stars;
		this.rgb = rgb;
		this.bookRgb = bookRgb;
		this.barColor = barColor;
		this.barOverlay = barOverlay;
		this.teamColor = teamColor;
		this.defaultName = defaultName;
	}

	public int stars() {
		return stars;
	}

	public String starText() {
		return Text.stars(stars);
	}

	/** Bright colour for names, chat and lore. */
	public int rgb() {
		return rgb;
	}

	public TextColor color() {
		return TextColor.fromRgb(rgb);
	}

	/** Darker variant that stays readable on the beige page of a book. */
	public int bookRgb() {
		return bookRgb;
	}

	public BossEvent.BossBarColor barColor() {
		return barColor;
	}

	public BossEvent.BossBarOverlay barOverlay() {
		return barOverlay;
	}

	public TeamColor teamColor() {
		return teamColor;
	}

	public String defaultName() {
		return defaultName;
	}

	public String id() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Ranks that can appear when a mob spawns. Ascendant only comes from promotion. */
	public boolean natural() {
		return this != ASCENDANT;
	}

	public BossRank up(int steps) {
		BossRank[] all = values();
		return all[Math.max(0, Math.min(all.length - 1, ordinal() + steps))];
	}

	/** Text in this rank's style: Legendary is bold gold, Ascendant a bold pearl shimmer. */
	public MutableComponent styled(String text) {
		if (this == ASCENDANT) {
			return Text.noItalic(Text.gradientText(text, ASCENDANT_GRADIENT, Style.EMPTY.withBold(true)));
		}
		Style style = Style.EMPTY.withColor(rgb);
		if (this == GOLD) {
			style = style.withBold(true);
		}
		return Component.literal(text).withStyle(style);
	}

	/** Legendary and above get the big effects. */
	public boolean top() {
		return atLeast(GOLD);
	}

	public boolean atLeast(BossRank other) {
		return ordinal() >= other.ordinal();
	}

	public static @Nullable BossRank parse(@Nullable String raw) {
		if (raw == null) {
			return null;
		}
		String s = raw.trim().toUpperCase(Locale.ROOT);
		for (BossRank rank : values()) {
			if (rank.name().equals(s)) {
				return rank;
			}
		}
		for (BossRank rank : values()) {
			if (rank.defaultName.replace(" ", "").equalsIgnoreCase(s.replace(" ", "").replace("_", ""))) {
				return rank;
			}
		}
		return null;
	}
}
