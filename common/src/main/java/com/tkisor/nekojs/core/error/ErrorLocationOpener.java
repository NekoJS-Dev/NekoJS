package com.tkisor.nekojs.core.error;

/**
 * Injectable dispatcher for error locations. An implementation only promises that the open
 * request was handed to the platform or process; callers must not read that as the editor
 * having actually opened.
 */
public interface ErrorLocationOpener {

    OpenResult open(LocalErrorSource.Target target);

    enum Status {
        /** The open request was accepted by a real dispatch mechanism. */
        DISPATCH_ACCEPTED,
        /** No dispatch mechanism is available, or the mechanism threw or rejected the request. */
        FAILED
    }

    record OpenResult(Status status, boolean lineRequested, String failure) {
        public static OpenResult accepted(boolean lineRequested) {
            return new OpenResult(Status.DISPATCH_ACCEPTED, lineRequested, null);
        }

        public static OpenResult failed(String failure) {
            return new OpenResult(Status.FAILED, false, failure == null || failure.isBlank() ? "unavailable" : failure);
        }

        public boolean accepted() {
            return status == Status.DISPATCH_ACCEPTED;
        }
    }
}
