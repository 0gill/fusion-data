package zer0g.fusion.data;

import java.util.HexFormat;

@FoType
public record BlobRef(@FoField(isKey = true) String hash, @FoField(range = "1") Long size, byte[] _bytes)
{
    public static final HexFormat HEX_FORMAT = HexFormat.of();

    public static BlobRef from(String json) {
        int doti = json.indexOf('.');
        String hash;
        Long length;
        if (-1 == doti) {
            hash = json;
            length = null;
        } else {
            hash = json.substring(0, doti);
            length = Long.parseLong(json.substring(doti + 1));
        }
        return new BlobRef(hash, length);
    }

    public BlobRef(String hash, Long length) {
        this(hash, length, null);
    }

    public BlobRef(byte[] hashBytes, Long length) {
        this(null, length, hashBytes);
    }

    public BlobRef {
        if (null != size && size < 0) {
            throw new IllegalArgumentException("negative size!");
        }
        if (null == hash) {
            hash = HEX_FORMAT.formatHex(_bytes);
        } else if (null == _bytes) {
            _bytes = HEX_FORMAT.parseHex(hash);
        } else {
            throw new IllegalArgumentException("_bytes must be null unless hash is null!");
        }
    }

    public String toJsonString() {
        return hash + "." + size;
    }
}
