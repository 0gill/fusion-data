package zer0g.fusion.data;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import static zer0g.fusion.data.NoCaseString.*;

/**
 * A fusion-object (FO) is effectively a JSON object, enhanced with Fusion features:
 * <ul>
 *     <li>Schema: Optional, but when supplied, enforces 'set' and 'get' value rules.</li>
 *     <li>Factory-method creation, with field-by-field and/or composite "initialization".</li>
 *     <li>Serialization to JSON in Fusion-canonical format (no whitespace; ordered fields).</li>
 *     <li>Deserialization from JSON with in-band validations.</li>
 *     <li>Easy and safe immutability:</li>
 *     <ul>
 *         <li>When created, an FoType is in INIT state, allowing ALL fields to be set.</li>
 *         <li>Once initialized, the {@link InitWriteReadStateData#doneInit()} method is called to transition to
 *         WRITE or READ state.</li>
 *         <li>In WRITE state, modifications are allowed per schema's constraints.</li>
 *         <li>In READ state, NO modifications are allowed.  This is a <b>final state</b></li>
 *         <li>ALL field values stored internally are immutable, and no references to internal structure(s) are
 *         leaked, so an FO in READ state is guaranteed immutable so long as JVM enforces class member access
 *         protections.</li>
 *     </ul>
 * </ul>
 */
public sealed interface FusionObject extends FusionData, InitWriteReadStateData, Comparable<FusionObject>
      permits FusionObjectBase, FusionBean
{
    /**
     * @return true if this is a "key" fob.
     */
    boolean isKey();

    default String toJsonString() {
        try (StringWriter stringWriter = new StringWriter()) {
            new JsonWriter(stringWriter).visitObject(this);
            return stringWriter.toString();
        } catch (IOException e) {
            throw new AssertionError();
        }
    }

    /**
     * Gets the native java data object wrapped inside the fusion value of the specified field.<b/>
     *
     * @param fieldName
     * @return {@code getfv(fieldName).getinner()}
     */
    default Object get(NoCaseString fieldName) {
        return getfv(fieldName).getinner();
    }

    default Object get(String fieldName) { return get(nocase(fieldName)); }

    /**
     * Gets the fusion-value assigned to the specified field.  Returns null if there is no such field.
     *
     * @param fieldName
     *       the field whose assigned value is to be returned
     * @return the fusion-value assigned to the key
     */
    FusionValue getfv(NoCaseString fieldName);

    default FusionObject set(String fieldName, Object value) {
        return set(nocase(fieldName), value);
    }

    /**
     * Assigns the supplied java-value to the specified field.  The java-value is automatically converted, if necessary
     * AND if possible, to conform to the field's value-domain (if any).  E.g. a Short will get converted to Long if the
     * object schema declares the field to be of type INTEGER (unqualified).  A Long MAY get converted to short, but
     * will throw ArithmeticException if value is too big.
     *
     * @param fieldName
     * @param value
     * @return
     */
    default FusionObject set(NoCaseString fieldName, Object value) {
        return set(indexOf(fieldName), value);
    }

    FusionObject set(int i, Object value);

    /**
     * The index of specified field in the object's field-list.
     *
     * @param fieldName
     *       name of the field
     * @return index, >= 0, of the field
     * @throws IllegalArgumentException
     *       if no such field exists
     */
    int indexOf(NoCaseString fieldName);

    /**
     * Note: The hashcode for the fusion-object should be the *primary-key*, if any.
     *
     * @param o
     *       the object to be compared.
     * @return
     */
    default int compareTo(FusionObject o) {
        if (o.getClass() != getClass()) {
            throw new ClassCastException(getClass().getName() + " not-comparable with " + o.getClass().getName());
        }
        if (o.schema() != schema()) {
            throw new ClassCastException(o.schema() + " not-comparable with " + schema());
        }
        if (schema() != null) {
            if (schema()._keyFields().isEmpty()) {
                throw new UnsupportedOperationException(
                      "Fusion-object of schema without key-fields cannot be compared: " + getClass().getName());
            }
            for (FusionFieldSchema keyField : schema()._keyFields()) {
                int c = getfv(keyField.name()).compareTo(o.getfv(keyField.name()));
                if (c != 0) {
                    return c;
                }
            }
            return 0;
        } else {
            // Compare the intersection fields; if there are NO intersection fields, throw an exception
            int count = 0;
            for (var fieldName : fieldNames()) {
                if (o.has(fieldName)) {
                    count++;
                    var c = getfv(fieldName).compareTo(o.getfv(fieldName));
                    if (c != 0) {
                        return c;
                    }
                }
            }
            if (count == 0) {
                throw new IllegalArgumentException("Cannot compare schema-less FO that have 0 fields in common!");
            }
            return 0;
        }
    }

    /**
     * @return {@code type().schema()}
     */
    default FusionObjectSchema schema() {
        return type().schema();
    }

    default Set<NoCaseString> fieldNames() {
        return asMap().keySet();
    }

    boolean has(NoCaseString fieldName);

    /**
     * The REQUIRED (inherent in the data class itself) fusion data-type of the object.
     */
    @Override
    FusionObjectType type();

    /**
     * Provides a {@link Map} view to this object.  The returned map is backed by the fusion-object, so any update made
     * to the map are applied to the fusion-object (via {@link #set(NoCaseString, Object)}.
     *
     * @return
     */
    Map<NoCaseString, FusionValue> asMap();

    @Override
    default void writeTo(Writer writer) throws IOException {
        new JsonWriter(writer).visitObject(this);
    }

    /**
     * This method cannot be overridden here, in an interface, so the only purpose of this declaration is this comment:
     * <ul>
     *     <li>{@link Object#hashCode()} for a fusion-object MAY return the same value for all instances of a
     *     particular fusion-object sub-class.</li>
     *     <li>For example: a {@link FusionObjectBase#hashCode()} returns hash-code of the class unless there are
     *     key fields, in which case it returns the hash-code of first key-field.</li>
     *     <li>Therefore, if two fusion-objects have the same hash-code they MAY not be equal.  But if they are
     *     equal, they WILL have same hashcode.</li>
     * </ul>
     */
    boolean equals(Object o);

    @Override
    default FusionObject cloneForWrite() throws FusionDataType.ValidationException {
        return (FusionObject) InitWriteReadStateData.super.cloneForWrite();
    }

    @Override
    FusionObject clone(IwrState wantedState);

    @Override
    default FusionObject cloneForInit() {
        return (FusionObject) InitWriteReadStateData.super.cloneForInit();
    }

    @Override
    default FusionObject cloneForRead() throws FusionDataType.ValidationException {
        return (FusionObject) InitWriteReadStateData.super.cloneForRead();
    }

    default FusionObject set(Path path, Object value) {
        state().requireWritable();
        var fieldName = nocase(path.getName(0).toString());
        if (path.getNameCount() == 1) {
            set(fieldName, value);
        } else {
            var curval = getfv(fieldName);
            if (null == curval || curval.isNull()) {
                throw new IllegalArgumentException("Null object encountered in set-path!");
            }
            switch (curval.type()) {
                case OBJECT -> ((FusionObjectBase) (curval.get())).set(path.subpath(1, path.getNameCount()), value);
                case LIST -> throw new UnsupportedOperationException("TODO: Cannot path-into lists yet!");
                default -> throw new IllegalArgumentException("Bad path: " + path);
            }
        }
        return this;
    }

    default Object get(Path path) {
        var fieldName = nocase(path.getName(0).toString());
        var fv = getfv(fieldName);
        if (null == fv || fv.isNull()) {
            return null;
        }
        if (path.getNameCount() == 1) {
            return fv.get();
        } else {
            return switch (fv.type()) {
                case OBJECT -> ((FusionObjectBase) fv.get()).get(path.subpath(1, path.getNameCount()));
                case LIST -> throw new UnsupportedOperationException("TODO: Cannot path-into lists yet!");
                default -> throw new IllegalArgumentException("Bad path: " + path);
            };
        }
    }

    default Object get(int i) {
        return getfv(i).getinner();
    }

    FusionValue getfv(int i);

    /**
     * Resets ALL fields to null.
     *
     * @throws IllegalStateException
     *       if fob state is NOT INIT
     */
    void resetRaw() throws IllegalStateException;

    /**
     * Resets all or subset of fields to their initial values when it was created anew via
     * {@link FusionObjectType#make()}.
     * <p/>
     * If state is write, then only writable fields are reset.  If state is read, an exception is thrown.
     */
    void reset() throws IllegalStateException;
}
