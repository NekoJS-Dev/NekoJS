// TODO(fabric): fabric 侧还没有对应实现，整文件守卫等移植完成后去掉
//? if neoforge {
package com.tkisor.nekojs.bindings.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.tkisor.nekojs.api.event.EventGroup;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * **脚本 API 表面 golden**：钉住 {@code BlockEvents} 命名空间对脚本可见的成员集合。
 *
 * <p>为什么需要它：SPI 抽取（共享声明 + 加载器适配）会改变字节码，所以"字节码逐字不变"
 * 对被重构的文件不再是正确的不变量。真正该守的不变量是**脚本看到的东西没变**——
 * 这正是本测试。它比字节码比对更贴近用户，且能抓到字节码抓不到的一类 bug：
 *
 * <p>典型场景：把 {@code GROUP} 搬到共享层后，{@code registry.register(GROUP)} 不再靠类
 * 初始化副作用登记 NeoForge 适配层的总线，而 {@code register} 之后组会 {@code freeze()}，
 * 于是 {@code randomTick} 静默消失（脚本报 {@code Binding 'BlockEvents' has no member
 * 'randomTick'}）。字节码比对全绿，只有真机才发现。本测试把这个场景变成 {@code check} 里
 * 的秒级断言：它按**生产顺序**（先 bootstrap 适配层、再冻结组）构造，然后比对成员集合。
 *
 * <p>golden 放在各节点的 {@code src/test/resources}（26.x 多一个 26-only 的
 * {@code modification}），改动 API 时更新对应 golden 并在 diff 里说明。
 */
class EventApiSurfaceGoldenTest {

    private static final String GOLDEN = "/golden/block-events-api.txt";

    @Test
    void blockEventsSurfaceMatchesGolden() throws IOException {
        // 生产顺序：适配层先登记总线，随后组被冻结（顺序反了会静默丢事件——见类注释）
        NeoForgeBlockEvents.bootstrap();
        EventGroup group = BlockEvents.GROUP;
        group.freeze();

        String actual = group.viewBuses().keySet().stream().sorted().collect(Collectors.joining("\n"));
        assertEquals(readGolden(), actual,
                "BlockEvents 的脚本可见成员集合变了。确认是刻意变更后，更新 " + GOLDEN);
    }

    private static String readGolden() throws IOException {
        try (InputStream stream = EventApiSurfaceGoldenTest.class.getResourceAsStream(GOLDEN)) {
            if (stream == null) {
                throw new IllegalStateException("缺少 golden 资源 " + GOLDEN);
            }
            List<String> lines = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .sorted()
                    .toList();
            return String.join("\n", lines);
        }
    }
}
//?}
