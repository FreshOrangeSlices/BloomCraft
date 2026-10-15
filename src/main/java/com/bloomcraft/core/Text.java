package com.bloomcraft.core;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Text helpers, including a small reader for the MiniMessage-style tags the config's messages use:
 * colour names ({@code <gray>}), hex colours ({@code <#FFAA00>}), {@code <bold>}, {@code <italic>},
 * {@code <underlined>}, {@code <strikethrough>}, {@code <obfuscated>}, {@code <reset>}, {@code <newline>},
 * {@code <gradient:#a:#b>...</gradient>}, closing tags, and placeholders such as {@code <boss>}.
 */
public final class Text {

	private Text() {
	}

	/** Parses a message template; placeholders are filled from {@code values} (e.g. "boss" -> the boss's name). */
	public static MutableComponent mm(String template, Map<String, Component> values) {
		return new Parser(template, values).parse();
	}

	public static MutableComponent mm(String template) {
		return mm(template, Map.of());
	}

	/** Escapes text so it isn't read as tags. */
	public static String escape(String text) {
		return text.replace("<", "\\<");
	}

	/** Item names and lore are italic by default; this turns that off (unless a part asks for italic). */
	public static MutableComponent noItalic(Component component) {
		return Component.empty().withStyle(Style.EMPTY.withItalic(false)).append(component);
	}

	public static MutableComponent line(String text, int rgb) {
		return noItalic(Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));
	}

	public static MutableComponent line(String text, ChatFormatting color) {
		return noItalic(Component.literal(text).withStyle(color));
	}

	public static MutableComponent colored(String text, int rgb) {
		return Component.literal(text).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)));
	}

	public static String plain(Component component) {
		return component.getString();
	}

	/** Breaks text into lines of about {@code width} characters at spaces (tooltips don't wrap by themselves). */
	public static List<String> wrap(String text, int width) {
		List<String> lines = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split("\\s+")) {
			if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
				lines.add(line.toString());
				line.setLength(0);
			}
			if (!line.isEmpty()) {
				line.append(' ');
			}
			line.append(word);
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	public static String stars(int count) {
		return "★".repeat(Math.max(0, count));
	}

	/** ZOMBIE_VILLAGER -> Zombie Villager, blood-pact -> Blood Pact, minecraft:wither_skeleton -> Wither Skeleton */
	public static String pretty(String raw) {
		String s = raw;
		int colon = s.indexOf(':');
		if (colon >= 0) {
			s = s.substring(colon + 1);
		}
		String[] parts = s.toLowerCase(Locale.ROOT).replace('-', '_').split("_");
		StringBuilder out = new StringBuilder();
		for (String part : parts) {
			if (part.isEmpty()) {
				continue;
			}
			if (!out.isEmpty()) {
				out.append(' ');
			}
			out.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return out.toString();
	}

	/** 2.0 -> "2", 1.25 -> "1.25", 0.333 -> "0.33" */
	public static String num(double value) {
		double rounded = Math.round(value * 100.0) / 100.0;
		if (rounded == Math.rint(rounded)) {
			return String.valueOf((long) rounded);
		}
		String s = String.format(Locale.ROOT, "%.2f", rounded);
		if (s.endsWith("0")) {
			s = s.substring(0, s.length() - 1);
		}
		return s;
	}

	public static String roman(int value) {
		if (value <= 0 || value > 3999) {
			return String.valueOf(value);
		}
		int[] numbers = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
		String[] letters = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
		StringBuilder out = new StringBuilder();
		for (int i = 0; i < numbers.length; i++) {
			while (value >= numbers[i]) {
				value -= numbers[i];
				out.append(letters[i]);
			}
		}
		return out.toString();
	}

	/** Interpolated colour between stops, t in [0, 1]. */
	public static int gradient(int[] stops, double t) {
		if (stops.length == 1) {
			return stops[0];
		}
		double pos = Math.max(0, Math.min(1, t)) * (stops.length - 1);
		int i = Math.min(stops.length - 2, (int) Math.floor(pos));
		double f = pos - i;
		int a = stops[i];
		int b = stops[i + 1];
		int r = (int) Math.round(((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
		int g = (int) Math.round(((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
		int bl = (int) Math.round((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
		return (r << 16) | (g << 8) | bl;
	}

	/** Each character of {@code text} coloured along the gradient. */
	public static MutableComponent gradientText(String text, int[] stops, Style base) {
		MutableComponent out = Component.empty();
		int n = text.codePointCount(0, text.length());
		int i = 0;
		for (int offset = 0; offset < text.length(); ) {
			int cp = text.codePointAt(offset);
			String ch = new String(Character.toChars(cp));
			int color = gradient(stops, n <= 1 ? 0 : (double) i / (n - 1));
			out.append(Component.literal(ch).withStyle(base.withColor(TextColor.fromRgb(color))));
			offset += Character.charCount(cp);
			i++;
		}
		return out;
	}

	// =====================================================================

	/** The 16 named colours (and "grey" spellings). */
	private static final Map<String, Integer> NAMED = Map.ofEntries(
		Map.entry("black", 0x000000), Map.entry("dark_blue", 0x0000AA), Map.entry("dark_green", 0x00AA00),
		Map.entry("dark_aqua", 0x00AAAA), Map.entry("dark_red", 0xAA0000), Map.entry("dark_purple", 0xAA00AA),
		Map.entry("gold", 0xFFAA00), Map.entry("gray", 0xAAAAAA), Map.entry("grey", 0xAAAAAA),
		Map.entry("dark_gray", 0x555555), Map.entry("dark_grey", 0x555555), Map.entry("blue", 0x5555FF),
		Map.entry("green", 0x55FF55), Map.entry("aqua", 0x55FFFF), Map.entry("red", 0xFF5555),
		Map.entry("light_purple", 0xFF55FF), Map.entry("yellow", 0xFFFF55), Map.entry("white", 0xFFFFFF));

	private static final class Parser {

		private final String in;
		private final Map<String, Component> values;
		private final MutableComponent root = Component.empty();
		/** Open tags, innermost last: tag name and the style before it was opened. */
		private final Deque<Object[]> open = new ArrayDeque<>();
		private Style style = Style.EMPTY;
		private final StringBuilder text = new StringBuilder();
		private int[] gradient;
		private Style gradientStyle;

		Parser(String in, Map<String, Component> values) {
			this.in = in;
			this.values = values;
		}

		MutableComponent parse() {
			int i = 0;
			while (i < in.length()) {
				char c = in.charAt(i);
				if (c == '\\' && i + 1 < in.length() && in.charAt(i + 1) == '<') {
					text.append('<');
					i += 2;
					continue;
				}
				if (c == '<') {
					int end = in.indexOf('>', i);
					if (end > i && tag(in.substring(i + 1, end))) {
						i = end + 1;
						continue;
					}
				}
				text.append(c);
				i++;
			}
			flush();
			return root;
		}

		private void flush() {
			if (text.isEmpty()) {
				return;
			}
			if (gradient != null) {
				root.append(gradientText(text.toString(), gradient, gradientStyle));
			} else {
				root.append(Component.literal(text.toString()).withStyle(style));
			}
			text.setLength(0);
		}

		/** Handles one tag; returns false if it isn't one (it's then printed as text). */
		private boolean tag(String raw) {
			String t = raw.trim();
			if (t.isEmpty()) {
				return false;
			}
			if (t.startsWith("/")) {
				String name = t.substring(1).toLowerCase(Locale.ROOT);
				int colon = name.indexOf(':');
				if (colon >= 0) {
					name = name.substring(0, colon);
				}
				close(name);
				return true;
			}
			String lower = t.toLowerCase(Locale.ROOT);
			if (values.containsKey(t) || values.containsKey(lower)) {
				flush();
				Component value = values.containsKey(t) ? values.get(t) : values.get(lower);
				root.append(Component.empty().withStyle(style).append(value));
				return true;
			}
			if (lower.equals("newline") || lower.equals("br")) {
				text.append('\n');
				return true;
			}
			if (lower.equals("reset")) {
				flush();
				open.clear();
				style = Style.EMPTY;
				gradient = null;
				return true;
			}
			if (lower.startsWith("gradient")) {
				flush();
				List<Integer> stops = new ArrayList<>();
				for (String part : t.split(":")) {
					Integer rgb = color(part);
					if (rgb != null) {
						stops.add(rgb);
					}
				}
				if (stops.isEmpty()) {
					stops.add(0xFFFFFF);
				}
				open.addLast(new Object[]{"gradient", style, gradient, gradientStyle});
				gradient = stops.stream().mapToInt(Integer::intValue).toArray();
				gradientStyle = style;
				return true;
			}
			Style next = switch (lower) {
				case "bold", "b" -> style.withBold(true);
				case "italic", "i", "em" -> style.withItalic(true);
				case "underlined", "u" -> style.withUnderlined(true);
				case "strikethrough", "st" -> style.withStrikethrough(true);
				case "obfuscated", "obf" -> style.withObfuscated(true);
				case "!italic" -> style.withItalic(false);
				case "!bold" -> style.withBold(false);
				default -> {
					Integer rgb = color(lower.startsWith("color:") ? lower.substring(6) : lower);
					yield rgb == null ? null : style.withColor(TextColor.fromRgb(rgb));
				}
			};
			if (next == null) {
				return false;
			}
			flush();
			String name = lower.startsWith("#") || color(lower) != null ? "color" : lower;
			open.addLast(new Object[]{name, style, gradient, gradientStyle});
			style = next;
			if (gradient != null) {
				gradientStyle = next;
			}
			return true;
		}

		private void close(String name) {
			flush();
			boolean isColor = name.startsWith("#") || color(name) != null || name.equals("color");
			// close the innermost matching tag (and anything opened inside it)
			List<Object[]> stack = new ArrayList<>(open);
			for (int k = stack.size() - 1; k >= 0; k--) {
				String openName = (String) stack.get(k)[0];
				if (openName.equals(name) || (isColor && openName.equals("color"))
					|| (name.equals("b") && openName.equals("bold")) || (name.equals("i") && openName.equals("italic"))) {
					Object[] entry = stack.get(k);
					while (open.size() > k) {
						open.removeLast();
					}
					style = (Style) entry[1];
					gradient = (int[]) entry[2];
					gradientStyle = (Style) entry[3];
					return;
				}
			}
		}

		private static Integer color(String raw) {
			String s = raw.trim().toLowerCase(Locale.ROOT);
			if (s.startsWith("#") && s.length() == 7) {
				try {
					return Integer.parseInt(s.substring(1), 16);
				} catch (NumberFormatException ex) {
					return null;
				}
			}
			return NAMED.get(s);
		}
	}
}
