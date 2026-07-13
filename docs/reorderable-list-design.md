# 列表拖拽排序公共能力设计（Reorderable List）

> 目标：把 Library 文件夹、TagManager 标签里**各写一遍**的「长按拖拽换序」逻辑抽成一个可复用的
> Compose 能力，供当前及后续列表直接接入，行为、手感、震动、动画统一。

## 1. 背景与现状

目前至少两处实现了几乎相同的拖拽换序，代码重复且细节容易走偏：

- `feature/library/LibraryScreen.kt`（文件夹）：`LazyColumn` 上挂 `detectDragGesturesAfterLongPress`，
  容器级长按触发。
- `feature/create/tag/tagmanager/TagManagerScreen.kt`（标签）：由 `SwipeToDeleteRow` 检测长按后，
  经 `onReorderStart/Drag/End/Cancel` 驱动。

两者共享同一套模式（提炼）：

1. **本地可变副本** `items`：单一稳定对象；非拖拽时从数据源同步，拖拽中保持本地换序不被外部刷新打断。
2. 拖拽状态：`draggingIndex`、`draggedDistance`（累计 dy）、`initialItemOffset`/`initialItemSize`（拖起时布局）。
3. **命中测试**：`draggedCenter = initialItemOffset + draggedDistance + size/2`，在
   `listState.layoutInfo.visibleItemsInfo` 里找被中心覆盖、且非自身的行 → `items.add(target, removeAt(from))`，
   并把 `draggingIndex` 更新为 target。
4. 渲染：拖拽项用 `graphicsLayer { translationY = initialItemOffset + draggedDistance - 当前实时 offset }`
   纯跟手（按 key 定位自身实时 offset，避免换序那帧 index 错位抖动）+ `zIndex(1f)`；其余项挂 `animateItem()` 平滑让位。
5. 抬起震动（`HapticFeedbackType.LongPress`）；`onDragEnd` 回调 `onReorder(orderedKeys/items)`；`onDragCancel` 复位。

## 2. 目标 / 非目标

**目标**：一套泛型 `<T>`、与业务无关的可复用能力，封装上面 1–5；两种触发方式都支持；接入方只写「怎么渲染一行」和「换序结果怎么持久化」。

**非目标**：不含滑动删除（`SwipeToDeleteRow` 保持独立、与本能力正交组合）；不做跨列表拖拽、不做水平/网格拖拽（先只做垂直 `LazyColumn`，后续可扩展）。

## 3. 公共 API 设计

放在 `ui/components/reorder/`（跨模块共享 UI）。核心是一个状态持有者 + 两个 `Modifier` + 两种触发模式。

```kotlin
@Composable
fun <T> rememberReorderableListState(
    items: List<T>,                       // 数据源（外部真源，如 uiState.folders）
    key: (T) -> Any,                      // 稳定 key（文件夹名 / 标签 id）
    listState: LazyListState = rememberLazyListState(),
    onReorder: (List<T>) -> Unit,         // 抬起时回调换序后的完整有序列表
): ReorderableListState<T>

@Stable
class ReorderableListState<T> internal constructor(...) {
    val listState: LazyListState
    /** 供渲染的本地副本：拖拽中反映实时换序，非拖拽时同步自数据源。 */
    val items: List<T>
    fun isDragging(key: Any): Boolean

    // ── 命令式触发（模式 B：由外部手势/把手驱动，如 SwipeToDeleteRow）──
    fun onDragStart(key: Any)             // 记录 initialOffset/size、draggingIndex、震动
    fun onDrag(dy: Float)                 // 累计 + 命中测试 + 本地换序
    fun onDragEnd()                       // 回调 onReorder + 复位
    fun onDragCancel()
}

/** 模式 A：容器级长按拖拽（LibraryScreen 用）。挂在 LazyColumn 的 Modifier 上。 */
fun <T> Modifier.reorderable(state: ReorderableListState<T>, haptics: HapticFeedback): Modifier

/** 每个 item 的修饰：拖拽项跟手 + 置顶，其余项 animateItem。必须在 items{} 的 LazyItemScope 内调用。 */
fun LazyItemScope.reorderableItem(state: ReorderableListState<*>, key: Any): Modifier
```

要点：
- **本地副本 + 同步** 封装进 state（`LaunchedEffect(items, dragging)` 内部完成），接入方不再手写。
- **命中测试 / translationY** 封装进 state 与 `reorderableItem`（按 key 定位实时 offset，沿用现有防抖做法）。
- **两种触发模式**共用同一 state：模式 A 用 `Modifier.reorderable`（内部 `detectDragGesturesAfterLongPress` → 调 state 的 onDragStart/Drag/End）；模式 B 直接调 state 的命令式方法（TagManager 的 `SwipeToDeleteRow.onReorderStart/Drag/End` 转接过去）。

## 4. 使用示例

**Library 文件夹（模式 A，容器长按）：**

```kotlin
val haptics = LocalHapticFeedback.current
val reorder = rememberReorderableListState(
    items = folders, key = { it.name }, onReorder = { onReorder(it.map { f -> f.name }) },
)
LazyColumn(state = reorder.listState, modifier = Modifier.reorderable(reorder, haptics)) {
    itemsIndexed(reorder.items, key = { _, it -> it.name }) { _, folder ->
        FolderRow(folder, modifier = reorderableItem(reorder, folder.name))
    }
}
```

**TagManager 标签（模式 B，SwipeToDeleteRow 触发）：**

```kotlin
val reorder = rememberReorderableListState(items = uiState.tags, key = { it.id }, onReorder = { onReorderTags(it.map { t -> t.name }) })
LazyColumn(state = reorder.listState) {
    itemsIndexed(reorder.items, key = { _, it -> it.id }) { _, tag ->
        SwipeToDeleteRow(
            modifier = reorderableItem(reorder, tag.id),
            onReorderStart = { reorder.onDragStart(tag.id) },
            onReorderDrag  = { dy -> reorder.onDrag(dy) },
            onReorderEnd   = { reorder.onDragEnd() },
            onReorderCancel = { reorder.onDragCancel() },
        ) { TagRow(tag) }
    }
}
```

## 5. 关键点与边界

- **key 稳定**：换序与 translationY 定位都依赖稳定 key（现状：文件夹用 name、标签用 id）——保持一致。
- **拖拽中屏蔽外部刷新**：本地副本仅在 `draggingIndex == null` 时从数据源同步；拖拽中数据源更新（如 reload）不打断当前换序（现状已如此）。
- **行内编辑共存**：某行处于重命名态时不应参与拖拽渲染分支——接入方按需在该行改用普通 modifier（能力不感知业务，由接入方决定）。
- **完成回调**：`onReorder` 给出换序后的**完整有序列表**；接入方自行转成后端要的形态（Library 现在映射为 name→id 走 `PUT /folders/order`，Tag 走 `reorderTags`）。
- **contentPadding / spacing**：命中测试用的是 `layoutInfo` 的 offset（已含 padding），沿用现状即可。
- **可访问性/后续**：可留一个显式「拖拽把手」触发点（无障碍更友好）；网格/水平、跨列表拖拽留作后续扩展，不在本次范围。

## 6. 落地步骤

1. 新增 `ui/components/reorder/ReorderableListState.kt`：state + `rememberReorderableListState` + `Modifier.reorderable` + `LazyItemScope.reorderableItem`（把现有逻辑原样搬入、泛型化）。
2. 迁移 `LibraryScreen` 文件夹列表到模式 A，删除本地拖拽样板。
3. 迁移 `TagManagerScreen` 标签列表到模式 B（`SwipeToDeleteRow` 转接命令式方法），删除本地拖拽样板。
4. 回归两处手感（跟手、让位动画、震动、抬起持久化）；补一个交互/单测（换序结果顺序正确）。
