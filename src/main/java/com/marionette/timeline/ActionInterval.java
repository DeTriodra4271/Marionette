package com.marionette.timeline;

/**
 * One action block on the timeline: hold {@code type}'s button from
 * {@code start} to {@code end} (seconds). {@code end <= start} means an
 * instantaneous press (a single tick), which is how JUMP/ATTACK/USE are
 * normally used.
 */
public record ActionInterval(ActionType type, float start, float end) {
}
