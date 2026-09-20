package com.marionette.recording;

import java.util.List;

/**
 * A named, ordered sequence of frames — one "take".
 *
 * <p>The initial velocity is the player's motion at the instant recording
 * started, so playback can restore it before the first frame runs. Physics
 * is deterministic given identical inputs and starting state; starting
 * playback from a dead stop when the recording actually began mid-stride
 * (or mid-jump) is enough to make later, physics-sensitive actions like
 * jump-placing a block drift slightly from the original.
 */
public record Recording(
		String name,
		List<Frame> frames,
		double initialVelocityX,
		double initialVelocityY,
		double initialVelocityZ
) {
}
