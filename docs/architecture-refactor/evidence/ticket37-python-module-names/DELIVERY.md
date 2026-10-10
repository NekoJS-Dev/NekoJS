# 提交与精确制品映射

源码提交 `7588b67f632c8a16da86a419146759fcb0fa02ef`。相对基线 `b2895779661720f0f173c37c6a30d05d93c353a9` 的生产补丁精确匹配实机构建记录，SHA-256 `2d46d7a7997d91c0994ba7d1289ef2aa71d259ce040c738f5f6a1ca89d941b2b`。提交发生在本次实机验收后，没有把旧 JAR 的 SHA 套用到新源码。

| 节点 | 本次安装官方 JAR SHA-256 | 正常退出 |
|---|---|---|
| 1.21.1 | `bb6b48d75768d0a8d1afd9c3bc3fb82f991da7ee34f7a9276a6c66336fd4bedc` | 0 |
| 26.1.2 | `e731b4dc3f7a3adab6e70e0de346952972e77426e8398f7ee9779804d71e61b1` | 0 |
| 26.2.0 | `835a19db3a07b27c7e3f2fe4485a5ac91c7303917e0b98514e4ceb99d36666a1` | 0 |
| 26.1.2-fabric | `1b09e6fd6a32e4f99adeca58c62dc8afae7ecb87439db4eaaaacc6289420f2b3` | 0 |
| 26.2.0-fabric | `64195b2bae5aa9e1ead63510b03942bf44bf26f36d44f1f35aae2166721b719b` | 0 |

证据单独提交；推送后的精确远端核验独立进行。整体 NOT ACCEPTED，真实维护者结论 NOT RECORDED，本源码正式性能 NOT RUN。
