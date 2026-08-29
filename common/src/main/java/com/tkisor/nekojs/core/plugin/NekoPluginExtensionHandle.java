package com.tkisor.nekojs.core.plugin;

/**
 * 扩展点注册句柄：{@link NekoPluginExtensionRegistry#register} 返回的轻量凭据，
 * bootstrap 完成本扩展点后可凭它取回 finisher 产物。
 *
 * <p>句柄与单次 bootstrap 绑定（每次 bootstrap 重新注册扩展点、返回新句柄）。
 * 产物发布前调用 {@link #result()} 抛 {@link IllegalStateException}；
 * 扩展点被环境谓词跳过时本句柄永远保持未发布状态。
 */
public final class NekoPluginExtensionHandle<R> {

    private final String pointId;
    private volatile boolean finished;
    private volatile R product;

    NekoPluginExtensionHandle(String pointId) {
        this.pointId = pointId;
    }

    /** 句柄对应的扩展点 id。 */
    public String pointId() {
        return pointId;
    }

    /** 扩展点是否已在本轮 bootstrap 中完成（finisher 已运行并发布产物）。 */
    public boolean isFinished() {
        return finished;
    }

    /**
     * 取回 finisher 产物。
     *
     * @return 产物；finisher 返回 null 时为 {@code null}
     * @throws IllegalStateException 扩展点尚未完成（bootstrap 未跑完或该点被环境跳过）
     */
    public R result() {
        if (!finished) {
            throw new IllegalStateException(
                    "Plugin extension point '" + pointId + "' has not finished yet (bootstrap incomplete or point skipped)");
        }
        return product;
    }

    void publish(R product) {
        this.product = product;
        this.finished = true;
    }
}
