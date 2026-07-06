package dev.cypphi.minereels.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.cypphi.minereels.MineReels;
import dev.cypphi.minereels.video.FfmpegVideoStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.concurrent.locks.ReentrantLock;

/**
 * A dynamic texture fed by a {@link FfmpegVideoStream}. Pixel conversion runs
 * off-thread; the render thread only uploads a ready frame at a capped rate.
 */
public final class VideoSurface {
	private static final long WORKER_SLEEP_MILLIS = 8L;
	private static final long CLOSE_JOIN_MILLIS = 100L;

	private final int width;
	private final int height;
	private final int frameSize;
	private final long frameIntervalNanos;
	private final Identifier id;
	private final DynamicTexture texture;
	private final NativeImage image;
	private final FfmpegVideoStream stream;

	private final ReentrantLock imageLock = new ReentrantLock();
	private volatile boolean running = true;
	private volatile boolean everFilled = false;
	private volatile boolean dirty = false;
	private byte[] lastConsumed;
	private long lastFillNanos = 0L;
	private long lastUploadNanos = 0L;
	private final Thread worker;

	public VideoSurface(FfmpegVideoStream stream, int width, int height, int sequence, int maxUploadFps) {
		this.stream = stream;
		this.width = width;
		this.height = height;
		this.frameSize = width * height * 4;
		this.frameIntervalNanos = frameIntervalNanos(maxUploadFps);
		this.texture = new DynamicTexture(() -> "minereels-video", width, height, false);
		this.image = texture.getPixels();
		this.id = Identifier.fromNamespaceAndPath(MineReels.MOD_ID, "reel_video_" + sequence);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		this.worker = new Thread(this::run, "minereels-video-fill");
		this.worker.setDaemon(true);
		this.worker.start();
	}

	private void run() {
		while (running) {
			byte[] frame = stream.latestFrame();
			long now = System.nanoTime();
			if (frame != null && frame != lastConsumed && frame.length >= frameSize
					&& now - lastFillNanos >= frameIntervalNanos) {
				imageLock.lock();
				try {
					if (!running) {
						break;
					}
					try {
						fill(frame);
					} catch (IllegalStateException e) {
						running = false;
						MineReels.LOGGER.debug("Stopping video surface fill because the backing image is no longer allocated", e);
						break;
					}
					dirty = true;
					lastConsumed = frame;
					lastFillNanos = now;
					everFilled = true;
				} finally {
					imageLock.unlock();
				}
			}
			try {
				Thread.sleep(WORKER_SLEEP_MILLIS);
			} catch (InterruptedException e) {
				break;
			}
		}
	}

	private void fill(byte[] rgba) {
		int i = 0;
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int r = rgba[i] & 0xFF;
				int g = rgba[i + 1] & 0xFF;
				int b = rgba[i + 2] & 0xFF;
				int a = rgba[i + 3] & 0xFF;
				i += 4;
				image.setPixel(x, y, (a << 24) | (r << 16) | (g << 8) | b);
			}
		}
	}

	public void uploadIfReady() {
		long now = System.nanoTime();
		if (!dirty || now - lastUploadNanos < frameIntervalNanos || !imageLock.tryLock()) {
			return;
		}
		try {
			if (dirty) {
				try {
					texture.upload();
				} catch (IllegalStateException e) {
					running = false;
					dirty = false;
					MineReels.LOGGER.debug("Stopping video surface upload because the backing image is no longer allocated", e);
					return;
				}
				dirty = false;
				lastUploadNanos = now;
			}
		} finally {
			imageLock.unlock();
		}
	}

	public boolean hasFrame() {
		return everFilled;
	}

	public Identifier id() {
		return id;
	}

	public int width() {
		return width;
	}

	public int height() {
		return height;
	}

	public void close() {
		running = false;
		worker.interrupt();
		try {
			worker.join(CLOSE_JOIN_MILLIS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		imageLock.lock();
		try {
			Minecraft.getInstance().getTextureManager().release(id);
		} finally {
			imageLock.unlock();
		}
	}

	private static long frameIntervalNanos(int maxUploadFps) {
		return 1_000_000_000L / Math.max(1, maxUploadFps);
	}
}
