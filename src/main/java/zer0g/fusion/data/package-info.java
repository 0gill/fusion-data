/**
 * Foundation of Fusion's I-Oriented Application Framework.
 * <p/>
 * Provides fusion typed data units:
 * <ul>
 *     <li>{@link zer0g.fusion.data.FusionValue}</li>
 *     <li>{@link zer0g.fusion.data.FusionObject}</li>
 * </ul>
 * With following features:
 * <ul>
 *     <li>Canonical JSON serialization/deserialization</li>
 *     <li>Support for extended (closed-set) data-types: LocalTime, LocalDate, Instant, Duration, Blob, and Enum</li>
 *     <li>Support for "string sub-types" (open-set): path, nocase, url, uri, char</li>
 *     <li>Bytebuddy generated wrapper fusion-object (Fo) and fusion-object-type (FoType) classes for any plain-old java
 *     record or sub-interface of {@link zer0g.fusion.data.FusionBean} or sub-class of
 *     {@link zer0g.fusion.data.FusionBeanObject}</li>
 *     <li>Fusion value is immutable.</li>
 *     <li>Fusion object implements Init-Write-Read state-machine to support factory-method construction,
 *     modification (of field subset), and immutability guarantee via readonly end-state and no data-reference
 *     leakage.</li>
 *     <li>...</li>
 * </ul>
 */
package zer0g.fusion.data;
