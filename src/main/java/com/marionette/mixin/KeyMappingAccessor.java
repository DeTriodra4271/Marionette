package com.marionette.mixin;

import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Lets the recorder read and the player write a key's pressed state directly,
 * without needing a real hardware key event.
 *
 * <p>{@code clickCount} is peeked (never written) so the recorder can see
 * every real click even when clicks happen faster than the 20Hz tick rate —
 * it's the same counter vanilla drains via {@code consumeClick()} for its
 * own click handling, incremented straight from the raw input callback
 * rather than sampled once a tick, so nothing gets lost between samples.
 * Reading it doesn't consume it, so vanilla's own click handling (actually
 * placing/breaking blocks while you record) is unaffected.
 */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("isDown")
	boolean marionette$isDown();

	@Accessor("isDown")
	void marionette$setDown(boolean down);

	@Accessor("clickCount")
	int marionette$getClickCount();
}
