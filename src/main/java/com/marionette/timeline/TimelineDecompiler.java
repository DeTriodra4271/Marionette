package com.marionette.timeline;

import com.marionette.recording.Frame;
import com.marionette.recording.Recording;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The inverse of {@link TimelineCompiler}: turns a played-back
 * {@link Recording} (raw per-tick input, however it was produced — hand
 * recorded or authored) back into action blocks and look keyframes, so any
 * saved take can be opened and tweaked in the timeline editor instead of
 * only being creatable from scratch there.
 *
 * <p>Held keys become one block per contiguous run. Look direction is
 * lossy by necessity — a hand recording can carry a rotation keyframe's
 * worth of change every single tick — so keyframes are only emitted where
 * the direction actually moved by more than a small threshold, keeping the
 * decompiled timeline editable instead of a wall of overlapping diamonds.
 */
public final class TimelineDecompiler {
	private static final float ROTATION_KEYFRAME_THRESHOLD_DEGREES = 4f;

	private TimelineDecompiler() {
	}

	public record Decompiled(List<RotationKeyframe> rotations, List<ActionInterval> actions) {
	}

	public static Decompiled decompile(Recording recording) {
		List<Frame> frames = recording.frames();
		List<ActionInterval> actions = new ArrayList<>();

		extractRuns(frames, ActionType.FORWARD, Frame::forward, actions);
		extractRuns(frames, ActionType.BACK, Frame::back, actions);
		extractRuns(frames, ActionType.LEFT, Frame::left, actions);
		extractRuns(frames, ActionType.RIGHT, Frame::right, actions);
		extractRuns(frames, ActionType.JUMP, Frame::jump, actions);
		extractRuns(frames, ActionType.SNEAK, Frame::sneak, actions);
		extractRuns(frames, ActionType.SPRINT, Frame::sprint, actions);
		extractRuns(frames, ActionType.ATTACK, Frame::attack, actions);
		extractRuns(frames, ActionType.SWAP_HANDS, Frame::swapHands, actions);
		extractRuns(frames, ActionType.DROP, Frame::drop, actions);

		for (int tick = 0; tick < frames.size(); tick++) {
			float time = tick / 20f;
			int clicks = frames.get(tick).useClicks();
			for (int c = 0; c < clicks; c++) {
				actions.add(new ActionInterval(ActionType.USE, time, time));
			}
		}

		List<RotationKeyframe> rotations = extractRotationKeyframes(frames);

		return new Decompiled(rotations, actions);
	}

	private static void extractRuns(List<Frame> frames, ActionType type, Predicate<Frame> held, List<ActionInterval> out) {
		int runStart = -1;
		for (int tick = 0; tick < frames.size(); tick++) {
			boolean down = held.test(frames.get(tick));
			if (down && runStart < 0) {
				runStart = tick;
			} else if (!down && runStart >= 0) {
				out.add(new ActionInterval(type, runStart / 20f, tick / 20f));
				runStart = -1;
			}
		}
		if (runStart >= 0) {
			out.add(new ActionInterval(type, runStart / 20f, frames.size() / 20f));
		}
	}

	private static List<RotationKeyframe> extractRotationKeyframes(List<Frame> frames) {
		List<RotationKeyframe> rotations = new ArrayList<>();
		if (frames.isEmpty()) {
			return rotations;
		}

		Frame first = frames.get(0);
		rotations.add(new RotationKeyframe(0f, first.yaw(), first.pitch()));
		float lastYaw = first.yaw();
		float lastPitch = first.pitch();

		for (int tick = 1; tick < frames.size(); tick++) {
			Frame frame = frames.get(tick);
			float deltaYaw = Math.abs(Mth.wrapDegrees(frame.yaw() - lastYaw));
			float deltaPitch = Math.abs(frame.pitch() - lastPitch);
			if (deltaYaw + deltaPitch >= ROTATION_KEYFRAME_THRESHOLD_DEGREES) {
				rotations.add(new RotationKeyframe(tick / 20f, frame.yaw(), frame.pitch()));
				lastYaw = frame.yaw();
				lastPitch = frame.pitch();
			}
		}

		float lastTime = (frames.size() - 1) / 20f;
		if (rotations.get(rotations.size() - 1).time() < lastTime) {
			Frame last = frames.get(frames.size() - 1);
			rotations.add(new RotationKeyframe(lastTime, last.yaw(), last.pitch()));
		}

		return rotations;
	}
}
