package com.bloomcraft.bosses.guide;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Lays out text into written-book pages. It estimates how many lines each paragraph wraps to (using Minecraft's
 * default font widths) so that pages never overflow, whatever numbers the config produces.
 */
final class BookWriter {

	private static final int PAGE_WIDTH = 113; // pixels
	static final int PAGE_LINES = 14;

	private final List<Component> pages = new ArrayList<>();
	private MutableComponent page = Component.empty();
	private int used;

	/** 0-based index of the page currently being written. */
	int currentPage() {
		return pages.size();
	}

	void newPage() {
		if (used > 0) {
			pages.add(page);
		}
		page = Component.empty();
		used = 0;
	}

	/** Adds a paragraph (always ends with a line break). */
	void add(Component component, boolean bold) {
		int lines = lineCount(component.getString(), bold);
		if (used > 0 && used + lines > PAGE_LINES) {
			newPage();
		}
		page.append(component).append(Component.literal("\n"));
		used += lines;
	}

	void text(String text, int rgb) {
		add(Component.literal(text).withColor(rgb), false);
	}

	void heading(String text, int rgb) {
		add(Component.literal(text).withColor(rgb).withStyle(ChatFormatting.BOLD), true);
		blank();
	}

	/** A bold label followed by normal text, as one paragraph. */
	void entry(String label, int labelRgb, String body, int bodyRgb) {
		MutableComponent c = Component.empty()
			.append(Component.literal(label).withColor(labelRgb).withStyle(ChatFormatting.BOLD))
			.append(Component.literal(" " + body).withColor(bodyRgb));
		add(c, true); // estimated as if fully bold: slightly pessimistic, never overflows
	}

	void blank() {
		if (used == 0) {
			return;
		}
		if (used + 1 > PAGE_LINES) {
			newPage();
			return;
		}
		page.append(Component.literal("\n"));
		used++;
	}

	/** Keeps the next block together: starts a new page if fewer than {@code lines} remain. */
	void reserve(int lines) {
		if (used > 0 && used + lines > PAGE_LINES) {
			newPage();
		}
	}

	List<Component> finish() {
		newPage();
		return pages;
	}

	// ------------------------------------------------------------------

	static int lineCount(String text, boolean bold) {
		int lines = 0;
		for (String paragraph : text.split("\n", -1)) {
			lines += wrap(paragraph, bold);
		}
		return Math.max(1, lines);
	}

	private static int wrap(String paragraph, boolean bold) {
		if (paragraph.isEmpty()) {
			return 1;
		}
		int lines = 1;
		int x = 0;
		int space = width(' ', bold);
		for (String word : paragraph.split(" ")) {
			int w = 0;
			for (int i = 0; i < word.length(); i++) {
				w += width(word.charAt(i), bold);
			}
			int needed = (x == 0 ? 0 : space) + w;
			if (x + needed <= PAGE_WIDTH) {
				x += needed;
				continue;
			}
			if (w <= PAGE_WIDTH) {
				lines++;
				x = w;
				continue;
			}
			// A single word longer than the page: break it by characters.
			if (x > 0) {
				lines++;
				x = 0;
			}
			for (int i = 0; i < word.length(); i++) {
				int cw = width(word.charAt(i), bold);
				if (x + cw > PAGE_WIDTH) {
					lines++;
					x = 0;
				}
				x += cw;
			}
		}
		return lines;
	}

	private static int width(char c, boolean bold) {
		int w;
		if (c == ' ') {
			w = 4;
		} else if ("!,.:;|i'".indexOf(c) >= 0) {
			w = 2;
		} else if (c == 'l' || c == '`') {
			w = 3;
		} else if ("It[]".indexOf(c) >= 0) {
			w = 4;
		} else if ("fk<>(){}\"*".indexOf(c) >= 0) {
			w = 5;
		} else if (c == '@' || c == '~') {
			w = 7;
		} else if (c < 0x80) {
			w = 6;
		} else if (Character.isLetter(c) && c < 0x250) {
			w = 6;
		} else {
			w = 9; // symbols such as ★ ✦ ☠ ❤ are wider
		}
		return bold ? w + 1 : w;
	}
}
