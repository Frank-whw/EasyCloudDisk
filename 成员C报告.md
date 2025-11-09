我们已经做了什么
- 把 `sync` 核心组件都补齐了：`DirectoryWatcher` 能递归监听目录、自动给新子目录注册、还加了防抖，避免频繁刷新时事件炸裂；`SyncManager` 会按新增/修改/删除去调用远端接口，并且支持定时全量同步、冲突命名、失败自动重试。
- 完整打通了工具链：`HashCalculator` 做好了 SHA-256 和分块哈希，`CompressionService` 会按文件大小决定是否压缩，`ConflictResolver` 会给冲突文件自动改名，`RetryTemplate` 提供指数退避重试，`LoggerFactory` 和 `FileUtils` 也补了统一前缀、路径/磁盘工具。
- 单元测试写好了（放在 `src/main/java/com/clouddisk/client/Test/...`）：覆盖哈希、压缩/解压、冲突策略、重试逻辑（这只是在目前没办法运行的情况下写的测试，和DB大作业的那个测试很像，测试的是代码覆盖率。暂时不上传了，等A、B协调后再说）
- `pom.xml` 做了适配：排除了这些测试文件的主编译，让 build-helper 把自定义测试目录拉进来编译执行，结构保持在我们想要的位置。

还缺什么
- 成员 A 还没把最终版的 `ClientRuntimeContext` / 调度器交出来，现在 `SyncManager` 里只预留了注入和注册接口，一旦给出线程池、调度周期的细节，还要对接启动/关闭逻辑。
- 成员 B 的 `FileApiClient` 请求/响应约定还没敲定，我们现在调用只是占位，如果未来的接口字段或返回值不一样，还得再调一次。
- 测试层面：目录事件防抖的集成测试、模拟远端拉取/上传的 Mock API 还没做，等 A/B 的实现稳定下来再补比较靠谱。
- SyncManager 时序图还没写，准备等全链路跑通后一起更新，避免来回改。
