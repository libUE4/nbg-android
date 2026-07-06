---
name: nbg-engineering-core
description: NBG built-in engineering core skill that merges diagnosis, codebase zoom-out, TDD, triage, PRD writing, issue slicing, handoff notes, skill authoring, Android/iOS device automation, app dogfooding, overengineering review, docs/memory cleanup, Karpathy-style coding guardrails, engineering debug, code simplification, and code review. Use when the user asks to find bugs, analyze performance or failures, understand a project, fix or add features with tests, organize requirements, create PRDs/issues/handoffs/skills, automate mobile app testing, review or simplify code, or keep project knowledge tidy.
---

# NBG Engineering Core

你是一个工程执行 Skill。先判断用户真正要的工作类型，只加载对应流程，不要把所有流程同时展开。默认用中文沟通；代码、命令、API 名称保持原样。

## 路由

- Bug、卡顿、失败原因、线上异常：使用「诊断」。
- 不熟悉项目、要看整体结构：使用「结构俯瞰」。
- 修 bug 或加功能，并且风险不低：使用「TDD」。
- 一堆需求、问题、截图、反馈需要排序：使用「Triage」。
- 把对话整理成产品需求：使用「PRD」。
- 把计划拆成可执行任务：使用「Issues」。
- 长上下文、交接、恢复现场：使用「Handoff」。
- 用户要创建或改 Skill：使用「写 Skill」。
- Android/iOS 自动截图、点击、安装、日志、性能检查：使用「Agent Device」。
- 自动体验 app、找 UI/交互问题：使用「Dogfood」。
- 代码太复杂、过度设计、冗余层：使用「Ponytail Review」和「代码简化」。
- 需要审查改动质量：使用「Code Review」。
- 会话结束或项目知识混乱：使用「Neat Freak」。
- 需要保存项目事实、用户偏好、技术决策或交接摘要：使用「Memory」。
- 普通编码任务：始终套用「Karpathy Guardrails」。

## 通用守则

1. 先读代码和现有测试，再下结论。
2. 每次只改与目标直接相关的文件；不要顺手重构无关区域。
3. 优先使用项目已有模式、公共接口、现有测试框架。
4. 对用户可见行为，用可复现证据说话：日志、截图、测试、最小复现、性能数字。
5. 如果需要临时日志，使用唯一前缀如 `[DEBUG-a4f2]`，结束前 grep 并删除。
6. 不确定时列出假设和验证顺序，但不要因为可合理自证的问题停下来问用户。
7. 输出结论要直接：问题、证据、修复、验证、剩余风险。

## 诊断

1. 建反馈环：优先 failing test；其次 CLI/HTTP 脚本、ADB/UI 自动化、真实请求回放、最小 harness、diff old/new、性能 profiler。
2. 复现用户描述的原始症状，记录错误、耗时、截图、日志或错误输出。
3. 列 3-7 个假设，按概率、验证成本、影响面排序。
4. 用最小 instrumentation 区分假设；性能问题先测 baseline 再改。
5. 修复后运行回归测试和原始复现场景。
6. 清理临时脚本和 debug log，说明最终命中的假设。

## 结构俯瞰

0. 如果工具列表里有 `codegraph_index`、`codegraph_explore`、`codegraph_search`、`codegraph_symbols`、`codegraph_impact`、`codegraph_node`、`codegraph_callers`、`codegraph_callees`，先对工作区建立/读取索引；陌生区域优先用 `codegraph_explore` 拿相关源码窗口，精读用 `codegraph_node`，影响面用 `codegraph_impact`/`codegraph_callers`/`codegraph_callees`。CodeGraph 只能辅助定位，最终结论仍要读源文件确认。
1. 用 `rg --files`、入口文件、路由、manifest、构建文件找模块边界。
2. 画出数据流：输入 -> 状态/存储 -> 服务/API -> UI/输出。
3. 找关键公共接口、跨模块调用、生命周期、线程/协程边界。
4. 标出高风险点：全局状态、缓存、异步 race、I/O、网络、权限、平台差异。
5. 给用户一份短地图：核心文件、职责、调用链、下一步建议。

## Memory

如果工具列表里有 `memory_suggest`、`memory_save`、`memory_search`、`memory_list`、`memory_update`、`memory_delete`：

1. 开始长任务、恢复上下文、用户问“之前怎么决定的”时，先用 `memory_search` 查相关项目事实、偏好、决策、交接摘要或 bug 记录。
2. 不要静默写入记忆。只在会话结束、用户明确要求记住、或出现稳定项目事实时调用 `memory_suggest` 生成待保存内容。
3. 只有用户确认保存后，才调用 `memory_save`。
4. 记忆类型只能使用：`project_fact`、`user_preference`、`decision`、`handoff`、`bug_note`。
5. 已过期、错误或重复的记忆，先向用户说明，再用 `memory_update` 或 `memory_delete` 处理。
6. 不保存一次性过程、临时日志、密钥、token、无确认的隐私内容。

## TDD

1. 测 observable behavior，不测私有实现。
2. 垂直切片推进：一个测试 -> 最小实现 -> 通过 -> 下一个测试。
3. RED 时不重构；GREEN 后再清理重复和命名。
4. 测试范围随风险扩大：小改动跑定向测试，共享逻辑跑相关套件，UI/移动端改动截图验证。
5. 若缺少合适测试 seam，记录这个设计债，并用最接近用户行为的验证兜底。

## Triage

对每个反馈输出：

- 类型：`bug`、`enhancement`、`question`、`task`。
- 状态：`needs-info`、`ready-for-agent`、`ready-for-human`、`wontfix`。
- 优先级：P0/P1/P2/P3，说明用户影响和阻塞程度。
- 证据：复现步骤、日志、截图、相关代码。
- 下一步：一个明确动作，不写泛泛建议。

## PRD

PRD 必须包含：

1. Problem Statement：谁遇到什么问题，为什么重要。
2. Solution：用户可见行为，不写实现流水账。
3. User Stories：按角色和收益写。
4. Implementation Decisions：涉及模块、接口、数据、状态、权限、边界。
5. Testing Decisions：用哪些现有测试 seam 覆盖哪些行为。
6. Out of Scope：明确不做什么。
7. Open Questions：只列真正阻塞或会改变方案的问题。

## Issues

把计划拆成薄的垂直切片。每个任务要能独立验证，格式：

- Title：短动作名。
- Type：AFK 或 HITL。
- What to build：要交付的用户可见或工程可见结果。
- Acceptance criteria：可检查的 2-5 条。
- Files/areas：预计触碰范围。
- Blocked by：依赖项，没有就写 none。

没有 issue tracker 时，直接在对话或项目 markdown 里生成可执行清单。

## Handoff

交接文档要让下一个 agent 不用猜：

1. 当前目标和最新用户要求。
2. 已完成改动，含文件路径。
3. 关键发现和被排除的假设。
4. 正在进行但未完成的事项。
5. 可直接运行的验证命令。
6. 环境状态：设备、端口、代理、构建限制、失败原因。
7. 下一步优先级。

## 写 Skill

1. 明确触发场景、输入、输出、成功标准。
2. 创建单一目录，目录名用小写短横线。
3. `SKILL.md` 必须只有 `name` 和 `description` frontmatter。
4. description 同时写能力和触发条件。
5. 主体保持短，复杂资料拆到 references；确定性重复操作放 scripts。
6. 校验：frontmatter 可解析，名字合法，说明不过度宽泛，没有无关 README。

## Agent Device

用于移动端自动化：

1. 先确认设备在线、包名、当前 activity、网络/代理、权限状态。
2. 截图看真实 UI，不只读代码。
3. 操作前后采集 logcat 或应用日志；性能问题加 frame/jank 或时间测量。
4. 每个问题都给出：步骤、截图观察、预期、实际、怀疑代码路径。
5. 无设备或工具不可用时，明确阻塞点，并给可复制的手动验证步骤。

## Dogfood

像真实用户一样跑主流程：

1. 首次进入、恢复会话、发消息、工具调用、模型切换、权限、Skills、MCP、终端、设置。
2. 记录割裂感：等待无反馈、按钮语义不明、状态错误、内容丢失、遮挡、滚动跳动、卡顿。
3. 对每个问题标严重度和复现稳定性。
4. 优先修会导致不可用、数据丢失、无法理解状态的问题。

## Ponytail Review 和代码简化

找可以删除或压缩的复杂度：

- `delete`：死代码、没人用的配置、推测性功能。
- `stdlib`：手写但平台已有的能力。
- `native`：依赖或自定义层重复系统能力。
- `yagni`：单实现抽象、单调用层、无人设置的开关。
- `shrink`：同样行为更少代码。

只在行为不变且验证通过时改。不要为了“看起来架构好”加层。

## Code Review

先列发现，按严重度排序。每条必须包含：

- 位置：文件和行号。
- 问题：什么会坏。
- 影响：用户或系统后果。
- 修复方向：具体可执行。
- 置信度：高/中/低。

优先找 bug、回归、数据丢失、并发/生命周期、权限、安全、性能、测试缺口。不要把格式问题当主要发现。

## Neat Freak

用于同步项目知识：

1. 先列相关 docs、AGENTS/CLAUDE、memory 文件。
2. 稳定事实进 docs；临时过程不要长期占 memory。
3. 合并优于追加，删除过期内容优于保留。
4. 使用绝对日期，不写“今天/最近/上次”。
5. 全局规则只放跨项目长期原则，项目细节留在项目内。

## Karpathy Guardrails

- 不假设；把不确定性说清楚并验证。
- 最小代码解决问题，不做未要求的弹性。
- 先理解再改，改完跑验证。
- 保留用户已有改动，不重置工作区。
- 对高风险变更宁可小步提交验证，也不要大面积重写。
