package com.source.open.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.IOException;

public class CountingHttpServletRequestWrapper extends HttpServletRequestWrapper {

    private final CountingServletInputStream countingStream;

    public CountingHttpServletRequestWrapper(HttpServletRequest request) throws IOException {
        super(request);
        this.countingStream = new CountingServletInputStream(request.getInputStream());
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return countingStream;
    }

    public long getBytesRead() {
        return countingStream.getBytesRead();
    }

    private static class CountingServletInputStream extends ServletInputStream {
        private final ServletInputStream original;
        private long bytesRead = 0;

        public CountingServletInputStream(ServletInputStream original) {
            this.original = original;
        }

        @Override
        public int read() throws IOException {
            int b = original.read();
            if (b != -1) {
                bytesRead++;
            }
            return b;
        }

        @Override
        public int read(byte[] b) throws IOException {
            int count = original.read(b);
            if (count != -1) {
                bytesRead += count;
            }
            return count;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int count = original.read(b, off, len);
            if (count != -1) {
                bytesRead += count;
            }
            return count;
        }

        public long getBytesRead() {
            return bytesRead;
        }

        @Override
        public boolean isFinished() {
            return original.isFinished();
        }

        @Override
        public boolean isReady() {
            return original.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            original.setReadListener(readListener);
        }
    }
}
