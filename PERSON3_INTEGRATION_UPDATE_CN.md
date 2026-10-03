# Person 3 与 #55–#60 整合更新说明

本版本已经把 Person 3 的功能与当前 Game Engine 基础逻辑做了正式衔接。

## 本次实际修改的文件

```text
app/src/main/java/com/comp90018/deadline/domain/game/shuffle/BoardShuffler.kt
app/src/main/java/com/comp90018/deadline/domain/game/engine/DefaultGameEngine.kt
app/src/main/java/com/comp90018/deadline/domain/game/engine/GameEngine.kt
app/src/main/java/com/comp90018/deadline/feature/game/GameSensorActions.kt
app/src/main/java/com/comp90018/deadline/feature/game/GameSensorBinder.kt
app/src/main/java/com/comp90018/deadline/feature/game/GameUiState.kt
app/src/main/java/com/comp90018/deadline/feature/game/GameViewModel.kt
app/src/main/java/com/comp90018/deadline/feature/debug/Person3TestScreen.kt
```

## 关键变化

### #21 Shuffle

`DefaultGameEngine.shuffle()` 不再是空实现，而是调用 `BoardShuffler`。

Shuffle 现在只重新分配剩余 Board 上 Tile 的 `type`，并保持：

- Tile ID 不变
- Tile position / layer 不变
- Task Tray 不变
- OverlapGraph 仍然有效
- WON / LOST 状态下禁止 shuffle

因此可以和 #57 遮挡判断、#58 托盘、#59 三消、#60 胜负逻辑共存。

### #25 Shake-to-Shuffle

现在正式链路为：

```text
Accelerometer
→ ShakeDetector
→ GameSensorBinder
→ GameViewModel.onShuffleRequested()
→ GameEngine.shuffle()
→ GameState
```

Sensor 不再直接修改 Board。

只有当前游戏接受 shuffle 时，`GameSensorBinder` 才播放 `SHUFFLE` haptic。

### #27 Tilt-to-Peek

`GameViewModel` 保存 `peekAmount`，但不会改 GameState。
最终正式 `GameBoard` 完成后，使用 `tile.position.layer` 配合 `tiltPeek(...)` 即可。

### #28 Haptic

`GameViewModel.selectTile()` 已加入真实游戏事件对应的 haptic：

```text
普通成功选择 -> TILE_SELECT
三张消除     -> MATCH
胜利         -> SUCCESS
失败         -> FAILURE
```

Shake 成功由 `GameSensorBinder` 触发 `SHUFFLE`。

## 仍然需要团队后续完成的部分

目前正式 `GameScreen / GameBoard / TileView` 仍是空壳，因此 #27 的最终视觉效果仍需要在正式 Board UI 实现后接入。

当前 `Person3TestScreen.kt` 可以继续作为 Person 3 功能的独立测试页面。最终正式版本合并时，可以删除该测试页面并恢复正式 MainActivity / Navigation 入口。
