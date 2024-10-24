package zer0g.fusion.data;


import java.io.Closeable;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class Blob extends InitWriteReadStateData.Base
{
    public static final Base64.Encoder BYTES_ENCODER = Base64.getUrlEncoder().withoutPadding();
    public static final Base64.Decoder BYTES_DECODER = Base64.getUrlDecoder();
    private OutputStream _outStream;
    private final byte[] _bytes;
    private final ReadWriteLock _rwlock = new ReentrantReadWriteLock();

    public Blob(int length) {
        if (length < 0) {
            throw new IndexOutOfBoundsException(length);
        }
        _bytes = new byte[length];
    }

    public Blob(byte[] copy) {
        this(copy, copy.length);
    }

    public Blob(byte[] copy, int newLength) {
        _bytes = Arrays.copyOf(copy, newLength);
    }

    public Blob(String json) {
        _bytes = BYTES_DECODER.decode(json);
    }

    public byte get(int i) {
        return _bytes[i];
    }

    public void set(int i, byte value) {
        state().requireWritable();
        _bytes[i] = value;
    }

    public long length() {
        return _bytes.length;
    }

    /**
     * Allows reading directly from the contained buffer, thereby not wasting a copy (via {@link #bytesCopy()}) just to
     * read. (Blocks if another thread is waiting to acquire a writer-stream; returns when the writer is done.)<p/>
     *
     * @return a new input stream to read the blob's contents.
     * @throws IOException
     *       if there is an i/o error "loading" the blob's contents
     */
    public InputStream readerStream() throws IOException {
        return new InputStream()
        {
            private final BytesStream _bufStream = new BytesStream(_rwlock.readLock());

            @Override
            public int read() throws IOException {
                if (_bufStream.isClosed()) {
                    throw new EOFException();
                }
                return _bufStream.get();
            }

            @Override
            public void close() throws IOException {
                _bufStream.close();
            }
        };
    }

    /**
     * Allows writing directly into the contained buffer, if state is writable.
     * <p>
     * Note: Blocks if there are any open reader-streams; waits for them to close.<p/>
     *
     * @return the single output stream to write the blob's contents.
     * @throws IOException
     *       if previously returned output stream is not yet closed.
     */
    public synchronized OutputStream writerStream() throws IOException {
        state().requireWritable();

        if (null == _outStream) {
            _outStream = new OutputStream()
            {
                private final BytesStream _bytesStream = new BytesStream(_rwlock.writeLock());

                @Override
                public void write(int b) throws IOException {
                    if (_bytesStream.isClosed()) {
                        throw new EOFException();
                    }
                    _bytesStream.set((byte) b);
                }

                @Override
                public void close() throws IOException {
                    if (!_bytesStream.isClosed()) {
                        _bytesStream.close();
                        _outStream = null;
                    }
                }
            };
            return _outStream;
        } else {
            throw new IOException("There is already an unclosed writer!");
        }
    }

    @Override
    public String toString() {
        return BYTES_ENCODER.encodeToString(_bytes);
    }

    /**
     * Constructs a string from the bytes using {@link String#String(byte[], Charset)}
     *
     * @param charset
     *       the desired charset
     * @return blob's bytes as specified charset
     */
    public String as(Charset charset) {
        return new String(_bytes, charset);
    }

    public byte[] bytesCopy() {
        return bytesCopy(_bytes.length);
    }

    public byte[] bytesCopy(int newLength) {
        return Arrays.copyOf(_bytes, newLength);
    }

    @Override
    protected synchronized void prepForIwrStateChange(IwrState nextState) {
        if (_outStream != null) {
            throw new IllegalStateException("Blob still has open writer!");
        }
    }

    private final class BytesStream implements Closeable
    {
        private Lock _lock;
        private int _index;

        private BytesStream(Lock lock) {
            _lock = Objects.requireNonNull(lock);
            _lock.lock();
            _index = 0;
        }

        public void set(byte next) throws ArrayIndexOutOfBoundsException {
            _bytes[_index] = next;
            advance();
        }

        private void advance() {
            assert _index < length();
            if (isClosed()) {
                throw new IllegalStateException("Already closed!");
            }
            if (++_index == length()) {
                close();
            }
        }

        public boolean isClosed() {
            return -1 == _index;
        }

        @Override
        public void close() {
            if (!isClosed()) {
                _lock.unlock();
                _index = -1;
            }
        }

        public byte get() throws ArrayIndexOutOfBoundsException {
            var v = _bytes[_index];
            advance();
            return v;
        }
    }
}
