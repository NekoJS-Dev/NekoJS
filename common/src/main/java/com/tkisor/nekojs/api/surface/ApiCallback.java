package com.tkisor.nekojs.api.surface;

import java.util.List;

/**
 * Functional interface for a script callback: takes an argument list and returns a result.
 *
 * <p>Backed by a function from the script side. {@link #call(List)} may throw checked exceptions;
 * the caller decides how to propagate them.
 */
@FunctionalInterface
public interface ApiCallback {
    /** Calls the callback with the given argument list and returns its result; may throw. */
    Object call(List<Object> arguments) throws Exception;
}
