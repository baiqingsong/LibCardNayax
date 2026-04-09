# LibCardNayax

Nayax 刷卡支付模块，基于串口通信实现与 Nayax MDB 设备的交互，支持刷卡收款、取消、确认等完整支付流程。

## 功能特性

- 基于串口通信，支持 Nayax MDB 外设协议
- 自动设备初始化（查询状态 → 获取最小面额 → 就绪回调）
- 完整支付流程管理（发起收款 → 轮询 → 自动确认）
- 90 秒收款超时保护
- 串口断开自动重连（最多 5 次，指数退避）
- CRC16-Modbus 校验（发送 & 接收双向校验）
- 所有回调均在主线程执行

## 引入方式

在项目 `settings.gradle` 中添加：

```groovy
include ':nayax'
```

在 app 模块的 `build.gradle` 中添加依赖：

```groovy
dependencies {
    implementation project(path: ':nayax')
}
```

## 类说明

### NayaxManager

核心管理器，单例模式。负责串口连接、设备初始化、支付流程控制及自动重连。

#### 错误码常量

| 常量 | 值 | 说明 |
|------|---|------|
| `ERROR_PORT_OPEN` | 1 | 串口打开失败 |
| `ERROR_SEND` | 2 | 发送失败 |
| `ERROR_RECEIVE` | 3 | 接收异常 |
| `ERROR_PAYMENT_TIMEOUT` | 4 | 收款超时（90秒） |
| `ERROR_INVALID_RESPONSE` | 5 | 响应数据无效 |
| `ERROR_RECONNECT_EXHAUSTED` | 6 | 重连次数耗尽 |
| `ERROR_NOT_READY` | 7 | 设备未就绪 |

#### 公开方法

| 方法 | 说明 |
|------|------|
| `getInstance()` | 获取单例实例 |
| `setCallback(NayaxCallback)` | 设置回调监听 |
| `connect(int port)` | 连接串口并自动初始化设备 |
| `disconnect()` | 断开连接，清理状态 |
| `release()` | 释放所有资源并销毁单例 |
| `startPayment(float amount)` | 发起收款（金额必须大于 0） |
| `cancelPayment()` | 取消当前收款 |
| `confirmPayment()` | 手动确认收款完成（通常自动完成） |
| `reportSaleResult()` | 上报售卖成功结果 |
| `queryDeviceInfo()` | 手动重新查询设备信息 |
| `isConnected()` | 查询串口是否已连接 |
| `isReady()` | 查询设备是否初始化就绪 |
| `isPaying()` | 查询是否正在收款中 |

---

### NayaxCallback

回调接口，所有回调均在 **主线程** 执行。

| 回调方法 | 触发时机 | 参数说明 |
|---------|---------|---------|
| `onDeviceReady(version, deviceType, currencyCode, minAmount)` | 设备初始化完成 | `version`：控制板版本；`deviceType`：外设类型；`currencyCode`：货币代码；`minAmount`：最小面额 |
| `onPaymentStarted()` | 收款指令已被设备接受 | — |
| `onPaymentReceived(payType, amount)` | 检测到付款金额满足要求 | `payType`：支付方式；`amount`：实际收款金额 |
| `onPaymentCompleted(amount)` | 收款流程完成 | `amount`：最终收款金额 |
| `onPaymentCancelled()` | 收款已取消（设备确认） | — |
| `onSaleResult(success)` | 售卖结果已确认 | `success`：是否成功 |
| `onError(errorCode, message)` | 发生错误 | `errorCode`：错误码（见上表）；`message`：错误描述 |
| `onConnectionChanged(connected)` | 连接状态变化 | `connected`：是否已连接 |

---

### NayaxCommand

通讯指令构造器（工具类，不可实例化）。内部使用，通常不需要直接调用。

| 方法 | 说明 |
|------|------|
| `getMDBCommand()` | 获取 MDB 外设状态命令 |
| `getMinMoney()` | 获取最小面额命令 |
| `getStartMoney(amount, minAmount)` | 构建发起收款指令 |
| `getCompleteMoney()` | 构建完成收款指令 |
| `getCancelMoney()` | 构建取消收款指令 |
| `setSaleResult()` | 构建售卖结果指令 |
| `getMoney()` | 构建获取收款金额指令 |
| `validateResponse(response)` | 验证响应数据的 CRC16 校验 |

## 使用流程

```
connect() ──→ onDeviceReady() ──→ startPayment() ──→ onPaymentStarted()
                                                          │
                                                    (自动轮询收款)
                                                          │
                                                    onPaymentReceived()
                                                          │
                                                   (3秒后自动确认)
                                                          │
                                                   onPaymentCompleted()
                                                          │
                                                   reportSaleResult()
                                                          │
                                                    onSaleResult()
```

取消收款：

```
cancelPayment() ──→ onPaymentCancelled()
```

## 完整使用示例

### 1. 初始化与连接

```java
// 设置回调（建议在 onCreate 中调用）
NayaxManager.getInstance().setCallback(new NayaxCallback() {
    @Override
    public void onDeviceReady(String version, String deviceType, String currencyCode, float minAmount) {
        Log.i("Nayax", "设备就绪: version=" + version + " minAmount=" + minAmount);
        // 设备就绪后即可发起收款
    }

    @Override
    public void onPaymentStarted() {
        Log.i("Nayax", "收款已发起，等待刷卡...");
    }

    @Override
    public void onPaymentReceived(String payType, float amount) {
        Log.i("Nayax", "收到付款: " + amount + " 方式: " + payType);
    }

    @Override
    public void onPaymentCompleted(float amount) {
        Log.i("Nayax", "收款完成: " + amount);
        // 收款完成后上报售卖结果
        NayaxManager.getInstance().reportSaleResult();
    }

    @Override
    public void onPaymentCancelled() {
        Log.i("Nayax", "收款已取消");
    }

    @Override
    public void onSaleResult(boolean success) {
        Log.i("Nayax", "售卖结果: " + success);
    }

    @Override
    public void onError(int errorCode, String message) {
        Log.e("Nayax", "错误[" + errorCode + "]: " + message);
        // 根据 errorCode 做对应处理
    }

    @Override
    public void onConnectionChanged(boolean connected) {
        Log.i("Nayax", "连接状态: " + (connected ? "已连接" : "已断开"));
    }
});

// 连接串口（传入串口号，连接后自动初始化设备）
NayaxManager.getInstance().connect(3);
```

### 2. 发起收款

```java
// 确保设备已就绪（收到 onDeviceReady 回调后）
if (NayaxManager.getInstance().isReady()) {
    NayaxManager.getInstance().startPayment(1.50f);  // 收款 1.50 元
}
```

### 3. 取消收款

```java
// 在收款过程中取消
NayaxManager.getInstance().cancelPayment();
```

### 4. 上报售卖结果

```java
// 通常在 onPaymentCompleted 回调中调用
NayaxManager.getInstance().reportSaleResult();
```

### 5. 手动确认收款 (通常不需要)

```java
// 收款金额满足后系统会在 3 秒后自动确认
// 仅在需要手动干预时调用
NayaxManager.getInstance().confirmPayment();
```

### 6. 查询状态

```java
boolean connected = NayaxManager.getInstance().isConnected();  // 串口是否连接
boolean ready = NayaxManager.getInstance().isReady();          // 设备是否就绪
boolean paying = NayaxManager.getInstance().isPaying();        // 是否收款中
```

### 7. 断开与释放

```java
// 断开连接（可重新连接）
NayaxManager.getInstance().disconnect();

// 完全释放资源（销毁单例，通常在 Activity onDestroy 中调用）
NayaxManager.getInstance().release();
```

## 内部机制说明

### 自动初始化

调用 `connect()` 后，内部自动执行：
1. 打开串口（波特率 9600，HEX 模式）
2. 延迟 3 秒后查询 MDB 设备状态（自动重试，间隔 5 秒）
3. 获取状态后自动查询最小面额（自动重试，间隔 5 秒）
4. 面额获取成功后触发 `onDeviceReady` 回调，设备进入就绪状态

### 收款流程

调用 `startPayment()` 后：
1. 发送收款指令 → 等待设备确认 → 触发 `onPaymentStarted`
2. 自动轮询收款金额（每 2 秒一次）
3. 检测到付款金额 ≥ 请求金额 → 触发 `onPaymentReceived`
4. 3 秒后自动发送完成确认 → 设备确认后触发 `onPaymentCompleted`
5. 超过 90 秒未收到足额付款 → 触发 `onError(ERROR_PAYMENT_TIMEOUT, ...)`

### 自动重连

- **连接失败**：自动重试，指数退避（3s → 6s → 9s → 12s → 15s），最多 5 次
- **运行中断开**：检测到接收异常时自动重连，重置重试计数
- **超过重试上限**：触发 `onError(ERROR_RECONNECT_EXHAUSTED, ...)`
- **手动断开**（`disconnect()`/`release()`）：不会触发自动重连

### CRC 校验

- 所有发送指令自动附加 CRC16-Modbus 校验（低位在前）
- 所有接收数据自动验证 CRC，校验失败的数据被丢弃并记录日志
