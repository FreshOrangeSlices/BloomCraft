package com.bloomcraft.core;

import com.bloomcraft.BloomCraft;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Runs code later on the server thread: once after a delay, or repeatedly until it says stop. */
public final class Scheduler {

	private record Task(int runAt, int period, BooleanSupplier body) {
	}

	private static final List<Task> TASKS = new ArrayList<>();
	private static final List<Task> ADDED = new ArrayList<>();

	private Scheduler() {
	}

	/** Runs once, {@code delay} ticks from now. */
	public static void later(int delay, Runnable body) {
		ADDED.add(new Task(Ticks.now() + Math.max(1, delay), 0, () -> {
			body.run();
			return false;
		}));
	}

	/** Runs every {@code period} ticks (first after {@code delay}) for as long as it returns true. */
	public static void repeat(int delay, int period, BooleanSupplier body) {
		ADDED.add(new Task(Ticks.now() + Math.max(1, delay), Math.max(1, period), body));
	}

	/** Called once per server tick. */
	public static void tick() {
		TASKS.addAll(ADDED);
		ADDED.clear();
		int now = Ticks.now();
		List<Task> again = new ArrayList<>();
		Iterator<Task> it = TASKS.iterator();
		while (it.hasNext()) {
			Task task = it.next();
			if (now < task.runAt()) {
				continue;
			}
			it.remove();
			boolean keep;
			try {
				keep = task.body().getAsBoolean();
			} catch (RuntimeException ex) {
				BloomCraft.LOG.error("A scheduled task failed", ex);
				keep = false;
			}
			if (keep && task.period() > 0) {
				again.add(new Task(now + task.period(), task.period(), task.body()));
			}
		}
		TASKS.addAll(again);
	}

	/** Server stopped: drop everything. */
	public static void clear() {
		TASKS.clear();
		ADDED.clear();
	}
}
