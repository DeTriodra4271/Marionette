package com.marionette.timeline;

import com.marionette.recording.Frame;
import com.marionette.recording.Recording;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns an authored timeline (typed action blocks + look-direction
 * keyframes) into a {@link Recording} sampled at the same 20 ticks/second
 * the macro system already plays back correctly — so the timeline editor
 * doesn't need any playback logic of its own. Positions end up relative to
 * wherever the player is standing when it's played, exactly like a
 * hand-recorded take, since it's really just input, not position.
 */
public final class TimelineCompiler {
	private TimelineCompiler() {
	}

	public static Recording compile(String name, List<RotationKeyframe> rotations, List<ActionInterval> actions) {
		List<RotationKeyframe> sortedRotations = new ArrayList<>(rotations);
		sortedRotations.sort(Comparator.comparing(RotationKeyframe::time));

		List<TickInterval> tickIntervals = new ArrayList<>(actions.size());
		int totalTicks = 1;
		for (ActionInterval action : actions) {
			int start = Math.round(action.start() * 20f);
			int end = Math.round(action.end() * 20f);
			if (end <= start) {
				end = start + 1;
			}
			tickIntervals.add(new TickInterval(action.type(), start, end));
			totalTicks = Math.max(totalTicks, end);
		}
		for (RotationKeyframe kf : sortedRotations) {
			totalTicks = Math.max(totalTicks, Math.round(kf.time() * 20f) + 1);
		}

		List<Frame> frames = new ArrayList<>(totalTicks);
		for (int tick = 0; tick < totalTicks; tick++) {
			float time = tick / 20f;
			float[] rotation = interpolateRotation(sortedRotations, time);
			frames.add(new Frame(
					held(tickIntervals, ActionType.FORWARD, tick),
					held(tickIntervals, ActionType.BACK, tick),
					held(tickIntervals, ActionType.LEFT, tick),
					held(tickIntervals, ActionType.RIGHT, tick),
					held(tickIntervals, ActionType.JUMP, tick),
					held(tickIntervals, ActionType.SNEAK, tick),
					held(tickIntervals, ActionType.SPRINT, tick),
					held(tickIntervals, ActionType.ATTACK, tick),
					useClicksAt(tickIntervals, tick),
					held(tickIntervals, ActionType.SWAP_HANDS, tick),
					held(tickIntervals, ActionType.DROP, tick),
					rotation[0],
					rotation[1],
					0
			));
		}

		return new Recording(name, frames, 0, 0, 0);
	}

	private static boolean held(List<TickInterval> intervals, ActionType type, int tick) {
		for (TickInterval interval : intervals) {
			if (interval.type == type && tick >= interval.startTick && tick < interval.endTick) {
				return true;
			}
		}
		return false;
	}

	/**
	 * A USE block fires once, on its first tick — the timeline has no
	 * concept of a click rate, so a block just means "click here."
	 */
	private static int useClicksAt(List<TickInterval> intervals, int tick) {
		int clicks = 0;
		for (TickInterval interval : intervals) {
			if (interval.type == ActionType.USE && interval.startTick == tick) {
				clicks++;
			}
		}
		return clicks;
	}

	private static float[] interpolateRotation(List<RotationKeyframe> rotations, float time) {
		if (rotations.isEmpty()) {
			return new float[]{0f, 0f};
		}
		RotationKeyframe first = rotations.get(0);
		if (rotations.size() == 1 || time <= first.time()) {
			return new float[]{first.yaw(), first.pitch()};
		}
		RotationKeyframe last = rotations.get(rotations.size() - 1);
		if (time >= last.time()) {
			return new float[]{last.yaw(), last.pitch()};
		}
		for (int i = 0; i < rotations.size() - 1; i++) {
			RotationKeyframe a = rotations.get(i);
			RotationKeyframe b = rotations.get(i + 1);
			if (time >= a.time() && time <= b.time()) {
				float span = b.time() - a.time();
				float t = span <= 0f ? 0f : (time - a.time()) / span;
				float yaw = a.yaw() + Mth.wrapDegrees(b.yaw() - a.yaw()) * t;
				float pitch = Mth.lerp(t, a.pitch(), b.pitch());
				return new float[]{yaw, pitch};
			}
		}
		return new float[]{last.yaw(), last.pitch()};
	}

	private record TickInterval(ActionType type, int startTick, int endTick) {
	}
}
