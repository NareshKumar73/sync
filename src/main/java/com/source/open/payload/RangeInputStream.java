package com.source.open.payload;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class RangeInputStream extends FilterInputStream {

	long remaining;

	public RangeInputStream(InputStream in, long maxBytes) {
		super(in);
		this.remaining = maxBytes;
	}

	@Override
	public int read() throws IOException {
		if (remaining <= 0)
			return -1;
		int b = super.read();
		if (b != -1)
			remaining--;
		return b;
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		if (remaining <= 0)
			return -1;
		len = (int) Math.min(len, remaining);
		int read = super.read(b, off, len);
		if (read != -1)
			remaining -= read;
		return read;
	}

}
