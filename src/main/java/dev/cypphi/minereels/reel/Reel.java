package dev.cypphi.minereels.reel;

/**
 * Provider-agnostic representation of a single short-form video ("reel").
 *
 * Media URLs are nullable so a provider can supply metadata before (or without)
 * resolvable media — the renderer falls back to a placeholder card.
 */
public record Reel(
		String id,
		String author,
		String caption,
		long likeCount,
		String thumbnailUrl,
		String videoUrl,
		boolean liked
) {
	/** Returns a copy with a different liked state. */
	public Reel withLiked(boolean newLiked) {
		return new Reel(id, author, caption, likeCount, thumbnailUrl, videoUrl, newLiked);
	}
}
