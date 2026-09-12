package com.tkisor.nekojs.core.error;

/**
 * 可注入的错误位置分派器。实现只承诺“打开请求已交给平台/进程”，不得把它解释为编辑器确实打开。
 */
public interface ErrorLocationOpener {

    OpenResult open(LocalErrorSource.Target target);

    enum Status {
        /** 打开请求已被真实分派机制接受。 */
        DISPATCH_ACCEPTED,
        /** 没有可用分派机制，或分派机制抛出/拒绝了请求。 */
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
