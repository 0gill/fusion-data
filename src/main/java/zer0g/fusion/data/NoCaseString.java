package zer0g.fusion.data;

import java.util.Objects;

/**
 * A character-sequence that ignores case in comparisons.<p/>
 */
public final class NoCaseString implements CharSequence, Comparable<NoCaseString>
{
    private final String _original;

    public static NoCaseString nocase(String str) {
        return new NoCaseString(str);
    }

    public NoCaseString(String original) {
        _original = Objects.requireNonNull(original);
    }

    public String original() {
        return _original;
    }

    /**
     * Compares the original string with the one supplied, ignoring case.
     */
    @Override
    public int compareTo(NoCaseString o) {
        return original().compareToIgnoreCase(o.original());
    }

    @Override
    public int length() {
        return _original.length();
    }

    @Override
    public char charAt(int index) {
        return _original.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return new NoCaseString(original().substring(start, end));
    }

    @Override
    public String toString() {
        return _original;
    }

    @Override
    public int hashCode() {
        return _original.toLowerCase().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof NoCaseString))
            return false;
        return 0 == compareTo((NoCaseString) obj);
    }
}
