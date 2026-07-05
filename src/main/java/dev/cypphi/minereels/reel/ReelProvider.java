package dev.cypphi.minereels.reel;

import java.util.concurrent.CompletableFuture;

/**
 * Source of reels. Implementations must never block the render thread — all
 * fetching is async. The mock provider resolves immediately; the Instagram
 * provider does real network I/O behind the same contract.
 */
public interface ReelProvider {
	/** Human-readable name for logging/UI (e.g. "mock", "instagram"). */
	String name();

	/**
	 * Fetch a page of reels. The cursor is an opaque provider-defined token for
	 * pagination; null requests the first page.
	 */
	CompletableFuture<ReelPage> fetchFeed(String cursor);

	/** Toggle the like state for a reel. Returns the resulting liked state. */
	CompletableFuture<Boolean> toggleLike(String reelId, boolean liked);
}
