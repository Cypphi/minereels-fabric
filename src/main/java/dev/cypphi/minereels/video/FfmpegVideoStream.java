package dev.cypphi.minereels.video;

import dev.cypphi.minereels.MineReels;

import java.io.InputStream;
import java.util.List;

/**
 * Decodes a video URL to raw RGBA frames using the system {@code ffmpeg} as a
 * subprocess, reading fixed-size frames off its stdout on a daemon thread.
 * {@code -re} paces output to real time and {@code -stream_loop -1} loops the
 * clip, so {@link #latestFrame()} always returns the most recent frame (or null
 * until the first one arrives). No JNI, no bundled natives.
 *
 * Pure logic (no Minecraft types) — the frame bytes are turned into a texture
 * by per-version client code.
 */
public final class FfmpegVideoStream {
	private static final int MAX_FPS = 24;

	private final String url;
	private final int width;
	private final int height;
	private final int frameSize;

	private volatile byte[] latest;
	private volatile boolean running;
	private Process process;
	private Thread reader;

	public FfmpegVideoStream(String url, int width, int height) {
		this.url = url;
		this.width = width;
		this.height = height;
		this.frameSize = width * height * 4;
	}

	public void start() {
		running = true;
		reader = new Thread(this::run, "minereels-video");
		reader.setDaemon(true);
		reader.start();
	}

	private void run() {
		try {
			process = new ProcessBuilder(List.of(
					"ffmpeg", "-hide_banner", "-loglevel", "error",
					"-stream_loop", "-1", "-re",
					"-threads", "1",
					"-i", url,
					"-an",
					"-filter_threads", "1",
					"-f", "rawvideo", "-pix_fmt", "rgba",
					"-vf", "fps=" + MAX_FPS + ",scale=" + width + ":" + height + ":flags=fast_bilinear",
					"-"))
					// Discard stderr: on scroll we kill ffmpeg mid-write, which otherwise
					// spams a harmless "broken pipe" muxer error to the console.
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();

			InputStream in = process.getInputStream();
			while (running) {
				byte[] frame = in.readNBytes(frameSize);
				if (frame.length < frameSize) {
					break; // stream ended or broke
				}
				latest = frame;
			}
		} catch (Exception e) {
			if (running) {
				MineReels.LOGGER.warn("Video stream failed for {}", url, e);
			}
		}
	}

	/** The most recent decoded frame as RGBA bytes, or null if none yet. */
	public byte[] latestFrame() {
		return latest;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public void stop() {
		running = false;
		if (process != null) {
			process.destroy();
		}
		if (reader != null) {
			reader.interrupt();
		}
	}
}
