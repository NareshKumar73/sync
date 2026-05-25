package com.source.open.filter;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;

public class CountingHttpServletResponseWrapper extends HttpServletResponseWrapper {

    private CountingServletOutputStream countingStream;

    public CountingHttpServletResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() throws IOException {
        if (countingStream == null) {
            countingStream = new CountingServletOutputStream(super.getOutputStream());
        }
        return countingStream;
    }

    public long getBytesWritten() {
        return countingStream != null ? countingStream.getBytesWritten() : 0;
    }

    private static class CountingServletOutputStream extends ServletOutputStream {
        private final ServletOutputStream original;
        private long bytesWritten = 0;

        public CountingServletOutputStream(ServletOutputStream original) {
            this.original = original;
        }

        @Override
        public void write(int b) throws IOException {
            original.write(b);
            bytesWritten++;
        }

        @Override
        public void write(byte[] b) throws IOException {
            original.write(b);
            bytesWritten += b.length;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            original.write(b, off, len);
            bytesWritten += len;
        }

        public long getBytesWritten() {
            return bytesWritten;
        }

        @Override
        public boolean isReady() {
            return original.isReady();
        }

        @Override
        public void setWriteListener(WriteListener writeListener) {
            original.setWriteListener(writeListener);
        }
    }
}
