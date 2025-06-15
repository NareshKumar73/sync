package com.source.open.payload;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class ThrottledInputStream extends FilterInputStream {

	private final long maxBytesPerSec;
	private long remainingBytes;
	private long startTime = System.nanoTime();

	public ThrottledInputStream(InputStream in, long length, long maxBytesPerSec) {
		super(in);
		this.remainingBytes = length;
		this.maxBytesPerSec = maxBytesPerSec;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (remainingBytes <= 0)
			return -1;
		len = (int) Math.min(len, remainingBytes);
		len = (int) Math.min(len, maxBytesPerSec / 10); // chunk size
		int read = super.read(b, off, len);
		if (read > 0) {
			remainingBytes -= read;
			throttle(read);
		}
		return read;
	}

	@Override
	public int read() throws IOException {
		if (remainingBytes <= 0)
			return -1;
		int val = super.read();
		if (val != -1) {
			remainingBytes--;
			throttle(1);
		}
		return val;
	}

	private void throttle(int bytesRead) {
		long elapsed = (System.nanoTime() - startTime) / 1_000_000; // ms
		double expectedTime = (bytesRead * 1000.0) / maxBytesPerSec;
		if (expectedTime > elapsed) {
			try {
				Thread.sleep((long) (expectedTime - elapsed));
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
		startTime = System.nanoTime();
	}
}
