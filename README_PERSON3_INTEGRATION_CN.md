# Person 3 功能整合说明

这份 README 用于说明 **Person 3** 负责的功能、涉及的 Issue、增加/修改的文件，以及后续如何把这些功能整合到团队最终版本中。

---

## 一、Person 3 负责的 Issues

Person 3 负责以下 7 个 Issue：

- **#21** — 实现 Shuffle 游戏逻辑
- **#23** — 搭建 Sensor 基础框架
- **#24** — 实现 Shake Detection
- **#25** — 将 Shake 和 Shuffle 连接起来
- **#26** — 实现 Tilt-to-Peek 的传感器处理
- **#27** — 将 Tilt-to-Peek 整合到 Game UI
- **#28** — 实现 Haptic Feedback

整体功能可以概括为：

```text
Shuffle Logic
+
Sensor Infrastructure
+
Shake Detection
+
Shake-to-Shuffle
+
Tilt Processing
+
Tilt-to-Peek UI
+
Haptic Feedback
```

---

# 二、新增文件

## #21 — Shuffle 游戏逻辑

新增文件：

```text
app/src/main/java/com/comp90018/deadline/domain/game/shuffle/BoardShuffler.kt
```

作用：

- 提供游戏 Board / Tile 的 Shuffle 逻辑
- Shuffle 后保留原有元素，不丢失 Tile
- 只改变元素顺序
- Shuffle 逻辑放在 domain 层，不直接写在 UI 中

---

## #23 — Sensor Infrastructure

新增文件：

```text
app/src/main/java/com/comp90018/deadline/sensor/SensorInfrastructure.kt
```

作用：

- 提供 Android Sensor 的基础访问层
- 统一管理 `SensorManager`
- 为 Shake 和 Tilt 功能提供底层 Sensor 支持
- 避免各个功能重复直接操作 Android Sensor API

---

## #24 — Shake Detection

新增文件：

```text
app/src/main/java/com/comp90018/deadline/sensor/shake/ShakeDetector.kt
app/src/main/java/com/comp90018/deadline/sensor/shake/ShakeSensorController.kt
```

作用：

### `ShakeDetector.kt`

- 处理 Accelerometer 数据
- 判断当前是否发生有效 Shake
- 使用阈值避免轻微移动被误判为 Shake
- 使用 cooldown 避免一次摇动连续触发多次

### `ShakeSensorController.kt`

- 与 Android Accelerometer 连接
- 接收 Sensor 数据
- 把数据交给 `ShakeDetector`
- 当检测到 Shake 时触发上层 callback

---

## #25 — Shake-to-Shuffle

新增文件：

```text
app/src/main/java/com/comp90018/deadline/feature/game/GameSensorActions.kt
app/src/main/java/com/comp90018/deadline/feature/game/GameSensorBinder.kt
```

作用：

- 把 Shake Detection 与游戏 Shuffle 功能连接起来
- Sensor 层不直接修改 Game State
- Sensor 事件通过 Game 层接口传递

整体流程：

```text
Accelerometer
    ↓
ShakeDetector
    ↓
ShakeSensorController
    ↓
GameSensorBinder
    ↓
GameSensorActions.onShuffleRequested()
    ↓
GameViewModel / Game Engine
    ↓
BoardShuffler
```

---

## #26 — Tilt-to-Peek Sensor Processing

新增文件：

```text
app/src/main/java/com/comp90018/deadline/sensor/tilt/TiltProcessor.kt
app/src/main/java/com/comp90018/deadline/sensor/tilt/TiltSensorController.kt
```

作用：

### `TiltProcessor.kt`

- 处理手机倾斜数据
- 将设备角度转换成 Peek Amount
- Peek Amount 通常是一个归一化数值
- 加入 dead zone，减少手部轻微抖动造成的 UI 频繁移动

### `TiltSensorController.kt`

- 监听设备 Rotation Vector / Orientation Sensor
- 把 Sensor 数据传给 `TiltProcessor`
- 输出当前 Peek Amount

---

## #27 — Tilt-to-Peek UI

新增文件：

```text
app/src/main/java/com/comp90018/deadline/feature/game/TiltPeekUi.kt
```

作用：

- 提供 Compose UI 层的 Tilt-to-Peek 效果
- 根据 Peek Amount 调整下层元素的偏移/露出程度
- 用于实现玩家倾斜手机时“看见下层 Tile”的效果

注意：

目前最终 Game UI 还需要和团队负责的正式 `GameScreen` 进行整合。

当最终 Game Screen 完成以后，需要把 Game/ViewModel 中保存的：

```text
peekAmount
```

传入：

```text
tiltPeek(...)
```

Modifier 中。

---

## #28 — Haptic Feedback

新增文件：

```text
app/src/main/java/com/comp90018/deadline/sensor/haptic/HapticFeedbackManager.kt
```

作用：

- 提供 Android 震动 / Haptic Feedback
- 支持多种游戏反馈类型，例如：

```text
Tile Select
Match
Shuffle
Success
Failure
```

不同事件可以使用不同震动模式。

---

# 三、单元测试文件

新增测试文件：

```text
app/src/test/java/com/comp90018/deadline/domain/game/shuffle/BoardShufflerTest.kt

app/src/test/java/com/comp90018/deadline/sensor/shake/ShakeDetectorTest.kt

app/src/test/java/com/comp90018/deadline/sensor/tilt/TiltProcessorTest.kt
```

分别测试：

### #21

```text
BoardShufflerTest
```

主要验证：

- Shuffle 前后数量相同
- 元素没有丢失
- Slot / Value 保持完整
- 只是顺序发生变化

---

### #24

```text
ShakeDetectorTest
```

主要验证：

- 正常重力环境不会误触发 Shake
- 足够强的移动可以触发 Shake
- cooldown 内不会连续重复触发

---

### #26

```text
TiltProcessorTest
```

主要验证：

- 手机不倾斜时 Peek Amount 接近 0
- 倾斜幅度增加时 Peek Amount 增加
- Peek Amount 不会超过设定范围
- dead zone 正常工作

---

# 四、修改过的文件

## 1. AndroidManifest.xml

位置：

```text
app/src/main/AndroidManifest.xml
```

增加了：

```xml
<uses-permission android:name="android.permission.VIBRATE" />
```

作用：

- 用于 #28 Haptic Feedback

整合时注意：

不要直接覆盖团队最新的整个 `AndroidManifest.xml`。

只需要确认：

```xml
<uses-permission android:name="android.permission.VIBRATE" />
```

存在，并且放在：

```xml
<application>
```

标签之前即可。

---

## 2. MainActivity.kt

位置：

```text
app/src/main/java/com/comp90018/deadline/MainActivity.kt
```

当前为了测试 Person 3 功能，临时将原来的：

```text
Hello Android
```

默认页面改成了：

```text
Person3TestScreen
```

例如：

```kotlin
Person3TestScreen(
    lifecycleOwner = this@MainActivity
)
```

这个改动仅用于测试。

最终合并进团队主版本时：

```text
MainActivity.kt
```

应该恢复为团队正式的 App 入口。

不要直接用测试版 `MainActivity.kt` 覆盖团队最终版本。

---

# 五、临时测试页面

新增：

```text
app/src/main/java/com/comp90018/deadline/feature/debug/Person3TestScreen.kt
```

这个文件只用于测试 Person 3 的功能。

可以测试：

- **#23** Sensor Infrastructure
- **#25** Shake-to-Shuffle
- **#27** Tilt-to-Peek
- **#28** Haptic Feedback

测试页面目前显示：

```text
Sensor Status
Accelerometer Support
Rotation Vector Support
Shake Count
Shuffle Count
Current Board
Peek Amount
Tilt UI Demo
Haptic Test Buttons
```

---

# 六、哪些文件最终要保留

以下文件属于正式功能代码，建议保留：

```text
app/src/main/java/com/comp90018/deadline/domain/game/shuffle/BoardShuffler.kt

app/src/main/java/com/comp90018/deadline/sensor/SensorInfrastructure.kt

app/src/main/java/com/comp90018/deadline/sensor/shake/ShakeDetector.kt
app/src/main/java/com/comp90018/deadline/sensor/shake/ShakeSensorController.kt

app/src/main/java/com/comp90018/deadline/sensor/tilt/TiltProcessor.kt
app/src/main/java/com/comp90018/deadline/sensor/tilt/TiltSensorController.kt

app/src/main/java/com/comp90018/deadline/sensor/haptic/HapticFeedbackManager.kt

app/src/main/java/com/comp90018/deadline/feature/game/GameSensorActions.kt
app/src/main/java/com/comp90018/deadline/feature/game/GameSensorBinder.kt
app/src/main/java/com/comp90018/deadline/feature/game/TiltPeekUi.kt
```

测试文件也可以保留：

```text
BoardShufflerTest.kt
ShakeDetectorTest.kt
TiltProcessorTest.kt
```

---

# 七、哪些内容只是临时测试用

下面这些内容不一定需要进入最终 production 版本：

```text
app/src/main/java/com/comp90018/deadline/feature/debug/Person3TestScreen.kt
```

以及：

```text
MainActivity.kt
```

中临时跳转到：

```text
Person3TestScreen
```

的修改。

当正式 Game Screen 完成后：

- 可以删除 `Person3TestScreen.kt`
- `MainActivity.kt` 恢复正式 App 入口
- 正式 Game Screen 直接使用 Person 3 的 Sensor / Shuffle / Tilt / Haptic 功能

---

# 八、正式 Game 需要怎么接

当团队正式的：

```text
GameViewModel
GameScreen
```

完成以后，需要把 Person 3 的功能接进去。

---

## Shake-to-Shuffle

Game 层需要实现：

```kotlin
override fun onShuffleRequested() {
    // 调用正式 GameViewModel / Game Engine 的 Shuffle
}
```

对应流程：

```text
Shake
↓
GameSensorBinder
↓
onShuffleRequested()
↓
GameViewModel
↓
BoardShuffler
↓
更新 Board UI
```

---

## Tilt-to-Peek

Game 层需要接收：

```kotlin
override fun onPeekChanged(amount: Float) {
    // 保存当前 Peek Amount
}
```

然后 UI 使用：

```text
peekAmount
```

配合：

```kotlin
tiltPeek(...)
```

实现倾斜手机查看下层 Tile。

---

# 九、测试方式

## #23 — Sensor Infrastructure

测试内容：

```text
Accelerometer
Rotation Vector
```

是否显示：

```text
SUPPORTED
```

并且修改模拟器 Virtual Sensors 后，App 能收到变化。

---

## #25 — Shake-to-Shuffle

测试流程：

```text
Shake
↓
Shake Count +1
↓
Shuffle Count +1
↓
Board 顺序变化
```

同时需要确认：

- Tile 没有丢失
- Tile 数量不变
- cooldown 正常
- 一次 Shake 不会连续触发多次

---

## #27 — Tilt-to-Peek

测试流程：

```text
Tilt Device
↓
Peek Amount 改变
↓
UI 下层 Tile 发生位移 / 露出
```

建议使用：

```text
Android Emulator
→ Extended Controls
→ Virtual Sensors
```

或者直接使用 Android 真机。

---

## #28 — Haptic Feedback

测试：

```text
Tile Select
Match
Shuffle
Success
Failure
```

按钮调用时不应 Crash。

最终震动效果建议使用 Android 真机测试。

模拟器只能验证代码调用是否正常。

---

# 十、整合到 main 时的建议

合并 Person 3 代码到最新 `main` 时：

1. 保留所有正式功能文件
2. 保留单元测试
3. 将 `VIBRATE` permission 合并到最新 Manifest
4. 不要直接覆盖团队最新的 `MainActivity.kt`
5. `Person3TestScreen.kt` 仅作为 Debug/Test 使用
6. 将 `GameSensorBinder` 接入正式 `GameViewModel`
7. 将 `TiltPeekUi` 接入正式 Board UI
8. 合并后重新运行全部 Unit Test
9. Shake / Tilt / Haptic 最好使用 Android 真机再次测试

---

# 十一、总结

Person 3 当前已经完成的主要模块为：

```text
#21 Shuffle Logic

#23 Sensor Infrastructure

#24 Shake Detection

#25 Shake-to-Shuffle

#26 Tilt Processing

#27 Tilt-to-Peek UI

#28 Haptic Feedback
```

最终正式版本只需要将这些模块与团队的：

```text
GameViewModel
GameScreen
Game Engine
```

连接起来即可。

目前的 `Person3TestScreen` 只是用于独立测试 Person 3 功能，不是最终游戏页面。
