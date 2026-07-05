package dev.cypphi.minereels.video;

import dev.cypphi.minereels.MineReels;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.InputStream;
import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Decodes a video URL's audio track to PCM via the system {@code ffmpeg} and
 * plays it through a {@link SourceDataLine}, on a daemon thread. Runs in
 * parallel with {@link FfmpegVideoStream}; both use {@code -re} from the same
 * source so they stay roughly in sync (not sample-accurate).
 *
 * Volume is read live from the supplied 0..1 gain each buffer, so the config
 * slider takes effect immediately.
 */
public final class FfmpegAudioStream {
	private static final int SAMPLE_RATE = 48000;
	private static final AudioFormat FORMAT =
			new AudioFormat(SAMPLE_RATE, 16, 2, true, false); // s16le stereo

	private final String url;
	private final DoubleSupplier volume;

	private volatile boolean running;
	private Process process;
	private Thread thread;
	private SourceDataLine line;

	public FfmpegAudioStream(String url, DoubleSupplier volume) {
		this.url = url;
		this.volume = volume;
	}

	public void start() {
		running = true;
		thread = new Thread(this::run, "minereels-audio");
		thread.setDaemon(true);
		thread.start();
	}

	private void run() {
		try {
			line = AudioSystem.getSourceDataLine(FORMAT);
			line.open(FORMAT);
			line.start();

			process = new ProcessBuilder(List.of(
					"ffmpeg", "-hide_banner", "-loglevel", "error",
					"-stream_loop", "-1", "-re",
					"-i", url,
					"-vn",
					"-f", "s16le", "-ar", String.valueOf(SAMPLE_RATE), "-ac", "2",
					"-"))
					.redirectError(ProcessBuilder.Redirect.DISCARD)
					.start();

			InputStream in = process.getInputStream();
			byte[] buf = new byte[8192];
			int n;
			while (running && (n = in.read(buf)) >= 0) {
				applyVolume(buf, n, volume.getAsDouble());
				line.write(buf, 0, n);
			}
		} catch (Exception e) {
			if (running) {
				MineReels.LOGGER.warn("Audio stream failed for {}", url, e);
			}
		}
	}

	/** Scale signed 16-bit little-endian samples in place by gain (0..1). */
	private void applyVolume(byte[] buf, int len, double gain) {
		if (gain >= 0.999) {
			return;
		}
		double g = Math.max(0.0, gain);
		for (int i = 0; i + 1 < len; i += 2) {
			short sample = (short) ((buf[i] & 0xFF) | (buf[i + 1] << 8));
			int scaled = (int) (sample * g);
			buf[i] = (byte) scaled;
			buf[i + 1] = (byte) (scaled >> 8);
		}
	}

	public void stop() {
		running = false;
		if (process != null) {
			process.destroy();
		}
		if (line != null) {
			line.stop();
			line.flush();
			line.close();
		}
		if (thread != null) {
			thread.interrupt();
		}
	}
}
