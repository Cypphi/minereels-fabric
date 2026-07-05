package dev.cypphi.minereels.reel;

import java.util.List;

/** A page of reels plus the cursor to request the next page, if any. */
public record ReelPage(List<Reel> reels, String nextCursor) {
}
