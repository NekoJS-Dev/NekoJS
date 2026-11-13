package com.tkisor.nekojs.api.facade;

import com.tkisor.nekojs.api.annotation.Remap;
import com.tkisor.nekojs.api.data.CompoundBuilder;
import com.tkisor.nekojs.api.data.NbtEntry;
import com.tkisor.nekojs.api.data.NbtValue;

import java.util.List;

/**
 * NBT facade, exposed to scripts as the global object {@code NBT}.
 *
 * <p>Builds and inspects the platform-neutral {@link NbtValue} model: scalar and array
 * factories, SNBT parse/serialize, plain-object conversion, and data-directory file IO.
 * All values are immutable snapshots; file IO is limited to the mod data directory.
 * Invalid input (range overflow, malformed SNBT, non-compound write root) throws
 * {@link com.tkisor.nekojs.api.error.ApiInvocationException} with an
 * {@link com.tkisor.nekojs.api.error.ApiErrorCodes} code.
 */
public interface NbtFacade {
    /** Returns the given value unchanged; lets scripts normalize an existing {@link NbtValue} reference. */
    NbtValue of(NbtValue value);

    /**
     * Creates an empty compound builder. Scripts call it as {@code NBT.compound}, chain calls on the
     * builder, then convert the result to an NbtValue.
     */
    CompoundBuilder compound();

    // Scalar factories: the Java methods carry a Value suffix because byte/short/int/long/float/double
    // are Java keywords and cannot be method names, and @Remap maps them to the short JS names. @Remap
    // is the single source of truth for both Graal host access and ContractReflector symbol ids.
    /** Creates a byte value; the number must be integral and within byte range. */
    @Remap("byte") NbtValue byteValue(Number value);

    /** Creates a short value; the number must be integral and within short range. */
    @Remap("short") NbtValue shortValue(Number value);

    /** Creates an int value; the number must be integral and within int range. */
    @Remap("int") NbtValue intValue(Number value);

    /** Creates a long value from a signed decimal string (JS numbers cannot carry int64 losslessly). */
    @Remap("long") NbtValue longValue(String value);

    /** Creates a float value; the number must be finite and within float range. */
    @Remap("float") NbtValue floatValue(Number value);

    /** Creates a double value; the number must be finite. */
    @Remap("double") NbtValue doubleValue(Number value);

    /** Creates a byte array; every element must be integral and within byte range. */
    NbtValue byteArray(List<? extends Number> values);

    /** Creates an int array; every element must be integral and within int range. */
    NbtValue intArray(List<? extends Number> values);

    /** Serializes the value to an SNBT string. */
    String toSnbt(NbtValue value);

    /** Returns the kind name (e.g. {@code "COMPOUND"}, {@code "BYTE_ARRAY"}) of the value. */
    String kind(NbtValue value);

    /** Unwraps a scalar value to its Java object; long is returned as a {@code String}, non-scalar kinds return {@code null}. */
    Object scalar(NbtValue value);

    /** Returns the children of a list value; returns an empty list for non-list kinds. */
    List<NbtValue> values(NbtValue value);

    /** Returns the key/value entries of a compound value; returns an empty list for non-compound kinds. */
    List<NbtEntry> entries(NbtValue value);

    /** Reads a gzip-compressed compound from the data directory; returns {@code null} when the file does not exist. */
    NbtValue.CompoundValue read(String path);

    /** Writes the value as a gzip-compressed compound to the data directory; the root must be a compound. */
    void write(String path, NbtValue value);

    /** Parses an SNBT string into an NbtValue; the top level must be a compound or a list. */
    NbtValue parse(String snbt);

    /** Recursively converts an NbtValue into plain Java objects (Map/List/Number/String). */
    Object toObject(NbtValue value);

    /** Recursively converts plain Java objects (Map/List/Number/String/Boolean) into an NbtValue. */
    NbtValue fromObject(Object value);
}
