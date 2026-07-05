package dev.cypphi.minereels.input;

/**
 * Counts rapid consecutive taps of a key and resolves the total once a quiet
 * window elapses. Feed a tap with {@link #onTap()} on every key press and call
 * {@link #poll()} every client tick; poll returns the tap count (1, 2, 3, ...)
 * once no further tap has arrived within the window, otherwise 0.
 *
 * Pure logic, no Minecraft types, so it's shared by every version's client.
 * Note: a single tap only resolves after the window, so there is an inherent
 * ~{@code windowMillis} latency — that's intrinsic to multi-tap input.
 */
public final class MultiTapDetector {
	private final long windowMillis;
	private int taps;
	private long lastTapMillis;

	public MultiTapDetector(long windowMillis) {
		this.windowMillis = windowMillis;
	}

	public void onTap() {
		taps++;
		lastTapMillis = System.currentTimeMillis();
	}

	/** Returns the resolved tap count once the window has elapsed, else 0. */
	public int poll() {
		if (taps > 0 && System.currentTimeMillis() - lastTapMillis >= windowMillis) {
			int resolved = taps;
			taps = 0;
			return resolved;
		}
		return 0;
	}
}
