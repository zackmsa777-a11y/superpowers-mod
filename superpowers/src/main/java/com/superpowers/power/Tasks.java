package com.superpowers.power;

import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;

/** Tiny server-side scheduler for timed ability stages. */
public final class Tasks {
	private Tasks() {}

	private static final List<Task> ACTIVE = new ArrayList<>();
	private static final List<Task> PENDING = new ArrayList<>();

	private static final class Task {
		final Entity owner;
		int delay;
		final int duration;
		int tick;
		final IntConsumer body;

		Task(Entity owner, int delay, int duration, IntConsumer body) {
			this.owner = owner;
			this.delay = delay;
			this.duration = duration;
			this.body = body;
		}
	}

	/** Run once after {@code delay} ticks; cancelled if the owner is removed. */
	public static void later(Entity owner, int delay, Runnable r) {
		PENDING.add(new Task(owner, delay, 1, t -> r.run()));
	}

	/** Run {@code body(i)} for i = 0..duration-1, starting after {@code delay} ticks. */
	public static void repeat(Entity owner, int delay, int duration, IntConsumer body) {
		PENDING.add(new Task(owner, delay, duration, body));
	}

	public static void tick() {
		ACTIVE.addAll(PENDING);
		PENDING.clear();
		Iterator<Task> it = ACTIVE.iterator();
		while (it.hasNext()) {
			Task t = it.next();
			if (t.owner != null && t.owner.isRemoved()) {
				it.remove();
				continue;
			}
			if (t.delay > 0) {
				t.delay--;
				continue;
			}
			try {
				t.body.accept(t.tick);
			} catch (Exception e) {
				com.superpowers.Superpowers.LOGGER.error("Ability task failed", e);
				it.remove();
				continue;
			}
			if (++t.tick >= t.duration) it.remove();
		}
	}

	public static void clear() {
		ACTIVE.clear();
		PENDING.clear();
	}
}
