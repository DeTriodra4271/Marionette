package com.marionette.recording;

/** How a take behaves when it reaches its last tick. */
public enum LoopMode {
	/** Play once and stop. */
	OFF,
	/** Start again from wherever the player ended up (same spot within the block as recorded). */
	REPEAT,
	/** Put the player back exactly where this playback began before each repeat, so every lap is identical. */
	RETURN_TO_START
}
