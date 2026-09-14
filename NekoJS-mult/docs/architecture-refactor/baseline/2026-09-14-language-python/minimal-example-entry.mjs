// NekoJS Python 示例入口（工单 13）：以 ESM 身份加载 .py，并证明产物真的可执行。
// import 的相对路径由 Python 转译器的 moduleSpecifier 约定决定：
//   .py 里写 `import <name>` / `from <name> import <sym>` → './<name>'，解析器按
//   supportedExtensions 依次探测 .py / .js / index.*，因此 siblings 直接命中 .py。
import {
  greet,
  Counter,
  squares,
} from './minimal-example.py';

const counter = new Counter(1);
counter.add(2);
counter.add(3);

// 示例承诺的结果：greet("world") | 累加后的计数 | 平方和 | 首个平方 | 最后一个平方
globalThis.pythonExampleResult =
  greet('world') + '|' + counter.value + '|' + squares(4).reduce((a, b) => a + b, 0)
  + '|' + squares(4)[0] + '|' + squares(4)[3];
