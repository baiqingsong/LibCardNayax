package com.dawn.nayax;

import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import com.dawn.serial.LSerialUtil;

import java.lang.ref.WeakReference;

/**
 * Nayax刷卡设备管理器
 * <p>
 * 提供串口连接、设备初始化、支付流程管理及自动重连功能。
 * 使用单例模式，通过 {@link #getInstance()} 获取实例。
 * </p>
 * <p>使用流程：</p>
 * <ol>
 *   <li>{@link #setCallback(NayaxCallback)} 设置回调</li>
 *   <li>{@link #connect(int)} 连接串口（自动初始化设备）</li>
 *   <li>等待 {@link NayaxCallback#onDeviceReady} 回调</li>
 *   <li>{@link #startPayment(float)} 发起收款</li>
 *   <li>等待 {@link NayaxCallback#onPaymentCompleted(float)} 回调</li>
 *   <li>{@link #reportSaleResult()} 上报售卖结果</li>
 *   <li>{@link #disconnect()} 断开连接</li>
 * </ol>
 */
public class NayaxManager {

    private static final String TAG = "NayaxManager";

    // ==================== 错误码 ====================
    /** 串口打开失败 */
    public static final int ERROR_PORT_OPEN = 1;
    /** 发送失败 */
    public static final int ERROR_SEND = 2;
    /** 接收异常 */
    public static final int ERROR_RECEIVE = 3;
    /** 收款超时 */
    public static final int ERROR_PAYMENT_TIMEOUT = 4;
    /** 响应数据无效 */
    public static final int ERROR_INVALID_RESPONSE = 5;
    /** 重连次数耗尽 */
    public static final int ERROR_RECONNECT_EXHAUSTED = 6;
    /** 设备未就绪 */
    public static final int ERROR_NOT_READY = 7;

    // ==================== Handler消息类型 ====================
    private static final int MSG_QUERY_STATUS = 0x01;
    private static final int MSG_QUERY_MIN_AMOUNT = 0x02;
    private static final int MSG_POLL_PAYMENT = 0x03;
    private static final int MSG_PAYMENT_TIMEOUT = 0x04;
    private static final int MSG_COMPLETE_PAYMENT = 0x05;
    private static final int MSG_RECONNECT = 0x06;

    // ==================== 时间常量(ms) ====================
    private static final long INIT_QUERY_DELAY_MS = 3000L;
    private static final long STATUS_RETRY_MS = 5000L;
    private static final long MIN_AMOUNT_RETRY_MS = 5000L;
    private static final long PAYMENT_FIRST_POLL_MS = 3000L;
    private static final long PAYMENT_POLL_MS = 2000L;
    private static final long PAYMENT_TIMEOUT_MS = 90_000L;
    private static final long COMPLETE_DELAY_MS = 3000L;
    private static final long RECONNECT_BASE_DELAY_MS = 3000L;

    // ==================== 串口配置 ====================
    private static final int BAUD_RATE = 9600;
    private static final int MAX_RECONNECT_ATTEMPTS = 5;

    // ==================== 预期响应常量 ====================
    private static final String RESP_START_PAYMENT_OK = "E11020040003DC69";
    private static final String RESP_COMPLETE_OK = "E106100100010B6A";
    private static final String RESP_CANCEL_OK = "E10610020001FB6A";
    private static final String RESP_SALE_OK = "E10610030001AAAA";
    private static final int RESPONSE_LENGTH_STANDARD = 18;

    // ==================== 状态机 ====================
    private enum State {
        IDLE,
        QUERYING_STATUS,
        QUERYING_MIN_AMOUNT,
        STARTING_PAYMENT,
        POLLING_PAYMENT,
        COMPLETING_PAYMENT,
        CANCELLING_PAYMENT,
        REPORTING_SALE
    }

    // ==================== 单例 ====================
    private static volatile NayaxManager instance;

    // ==================== 核心字段 ====================
    private final Handler handler;
    private LSerialUtil serialUtil;
    private NayaxCallback callback;

    // ==================== 状态字段 ====================
    private State currentState = State.IDLE;
    private volatile boolean paying = false;
    private volatile boolean deviceReady = false;

    // ==================== 连接字段 ====================
    private int serialPort;
    private int reconnectCount = 0;

    // ==================== 设备与支付信息 ====================
    private float minAmount;
    private float requestedAmount;
    private float receivedAmount;
    private String deviceVersion;
    private String deviceType;
    private String currencyCode;

    // ==================== 构造 ====================

    private NayaxManager() {
        handler = new SafeHandler(this);
    }

    public static NayaxManager getInstance() {
        if (instance == null) {
            synchronized (NayaxManager.class) {
                if (instance == null) {
                    instance = new NayaxManager();
                }
            }
        }
        return instance;
    }

    // ==================== 公开API ====================

    /**
     * 设置回调监听
     */
    public void setCallback(NayaxCallback callback) {
        this.callback = callback;
    }

    /**
     * 连接串口并初始化设备
     * <p>连接成功后自动查询设备状态和最小面额，
     * 初始化完成后通过 {@link NayaxCallback#onDeviceReady} 回调通知。</p>
     *
     * @param port 串口号
     */
    public void connect(int port) {
        if (port < 0) {
            notifyError(ERROR_PORT_OPEN, "无效的串口号: " + port);
            return;
        }
        this.serialPort = port;
        this.reconnectCount = 0;
        openSerialPort();
    }

    /**
     * 断开串口连接
     */
    public void disconnect() {
        removeAllMessages();
        paying = false;
        deviceReady = false;
        currentState = State.IDLE;
        closeSerialPort();
    }

    /**
     * 释放所有资源并销毁单例
     */
    public void release() {
        disconnect();
        callback = null;
        synchronized (NayaxManager.class) {
            instance = null;
        }
    }

    /**
     * 发起收款
     *
     * @param amount 收款金额（必须大于0）
     */
    public void startPayment(float amount) {
        if (paying) {
            Log.w(TAG, "收款进行中，忽略重复请求");
            return;
        }
        if (!deviceReady || minAmount <= 0) {
            notifyError(ERROR_NOT_READY, "设备未就绪，请等待初始化完成");
            return;
        }
        if (amount <= 0) {
            notifyError(ERROR_SEND, "收款金额必须大于0");
            return;
        }

        paying = true;
        requestedAmount = amount;
        receivedAmount = 0;
        currentState = State.STARTING_PAYMENT;
        Log.i(TAG, "发起收款: " + amount);
        sendCommand(NayaxCommand.getStartMoney(amount, minAmount));
    }

    /**
     * 取消收款
     */
    public void cancelPayment() {
        if (!paying) {
            Log.w(TAG, "当前无收款进行中");
            return;
        }
        handler.removeMessages(MSG_POLL_PAYMENT);
        handler.removeMessages(MSG_PAYMENT_TIMEOUT);
        handler.removeMessages(MSG_COMPLETE_PAYMENT);
        currentState = State.CANCELLING_PAYMENT;
        sendCommand(NayaxCommand.getCancelMoney());
        // paying 状态在收到设备取消确认后才清除（handleCancelResponse）
    }

    /**
     * 手动确认收款完成
     * <p>通常由系统自动完成，仅在需要手动干预时调用</p>
     */
    public void confirmPayment() {
        currentState = State.COMPLETING_PAYMENT;
        sendCommand(NayaxCommand.getCompleteMoney());
    }

    /**
     * 上报售卖结果（成功）
     */
    public void reportSaleResult() {
        currentState = State.REPORTING_SALE;
        sendCommand(NayaxCommand.setSaleResult());
    }

    /**
     * 手动重新查询设备信息
     */
    public void queryDeviceInfo() {
        handler.removeMessages(MSG_QUERY_STATUS);
        handler.removeMessages(MSG_QUERY_MIN_AMOUNT);
        deviceReady = false;
        cycleQueryStatus();
    }

    /**
     * 串口是否已连接
     */
    public boolean isConnected() {
        return serialUtil != null && serialUtil.isConnected();
    }

    /**
     * 设备是否已初始化就绪
     */
    public boolean isReady() {
        return deviceReady;
    }

    /**
     * 是否正在收款
     */
    public boolean isPaying() {
        return paying;
    }

    // ==================== 串口管理 ====================

    private void openSerialPort() {
        closeSerialPort();

        serialUtil = new LSerialUtil(serialPort, BAUD_RATE, LSerialUtil.SerialType.TYPE_HEX,
                new LSerialUtil.OnSerialListener() {
                    @Override
                    public void onOpenError(String portPath, Exception e) {
                        Log.e(TAG, "串口打开失败: " + portPath, e);
                    }

                    @Override
                    public void onReceiveError(Exception e) {
                        Log.e(TAG, "串口接收异常", e);
                        notifyError(ERROR_RECEIVE, "串口接收异常: " + e.getMessage());
                        // 接收异常通常意味着串口断开，尝试重连
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (serialUtil != null && !serialUtil.isConnected()) {
                                    onRuntimeDisconnect();
                                }
                            }
                        });
                    }

                    @Override
                    public void onSendError(Exception e) {
                        Log.e(TAG, "串口发送异常", e);
                        notifyError(ERROR_SEND, "串口发送异常: " + e.getMessage());
                    }

                    @Override
                    public void onDataReceived(String data) {
                        if (!TextUtils.isEmpty(data)) {
                            Log.d(TAG, "收到数据: " + data);
                            handleSerialData(data);
                        }
                    }
                });

        // 使用 isConnected() 判断是否打开成功（比回调flag可靠）
        if (!serialUtil.isConnected()) {
            serialUtil = null;
            handlePortOpenFailed();
            return;
        }

        reconnectCount = 0;
        notifyConnectionChanged(true);
        handler.sendEmptyMessageDelayed(MSG_QUERY_STATUS, INIT_QUERY_DELAY_MS);
    }

    private void closeSerialPort() {
        boolean wasConnected = isConnected();
        if (serialUtil != null) {
            try {
                serialUtil.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "关闭串口异常", e);
            }
            serialUtil = null;
        }
        if (wasConnected) {
            notifyConnectionChanged(false);
        }
    }

    private void handlePortOpenFailed() {
        notifyError(ERROR_PORT_OPEN, "串口打开失败，端口: " + serialPort);
        attemptReconnect();
    }

    private void attemptReconnect() {
        reconnectCount++;
        if (reconnectCount > MAX_RECONNECT_ATTEMPTS) {
            notifyError(ERROR_RECONNECT_EXHAUSTED,
                    "重连失败，已达最大重试次数: " + MAX_RECONNECT_ATTEMPTS);
            return;
        }
        long delay = RECONNECT_BASE_DELAY_MS * reconnectCount;
        Log.i(TAG, "准备第 " + reconnectCount + " 次重连，延迟 " + delay + "ms");
        handler.sendEmptyMessageDelayed(MSG_RECONNECT, delay);
    }

    /**
     * 运行中串口断开处理
     */
    private void onRuntimeDisconnect() {
        Log.w(TAG, "运行中串口断开");
        removeAllMessages();
        if (paying) {
            paying = false;
            notifyError(ERROR_RECEIVE, "收款过程中串口断开");
        }
        deviceReady = false;
        currentState = State.IDLE;
        closeSerialPort();
        reconnectCount = 0;
        attemptReconnect();
    }

    // ==================== 轮询方法 ====================

    private void cycleQueryStatus() {
        handler.removeMessages(MSG_QUERY_STATUS);
        currentState = State.QUERYING_STATUS;
        sendCommand(NayaxCommand.getMDBCommand());
        handler.sendEmptyMessageDelayed(MSG_QUERY_STATUS, STATUS_RETRY_MS);
    }

    private void cycleQueryMinAmount() {
        handler.removeMessages(MSG_QUERY_MIN_AMOUNT);
        currentState = State.QUERYING_MIN_AMOUNT;
        sendCommand(NayaxCommand.getMinMoney());
        handler.sendEmptyMessageDelayed(MSG_QUERY_MIN_AMOUNT, MIN_AMOUNT_RETRY_MS);
    }

    private void cyclePollPayment() {
        handler.removeMessages(MSG_POLL_PAYMENT);
        currentState = State.POLLING_PAYMENT;
        sendCommand(NayaxCommand.getMoney());
        handler.sendEmptyMessageDelayed(MSG_POLL_PAYMENT, PAYMENT_POLL_MS);
    }

    // ==================== 响应处理 ====================

    private void handleSerialData(String data) {
        if (TextUtils.isEmpty(data)) return;

        if (!NayaxCommand.validateResponse(data)) {
            Log.w(TAG, "CRC校验失败: " + data);
            return;
        }

        switch (currentState) {
            case QUERYING_STATUS:
                handleStatusResponse(data);
                break;
            case QUERYING_MIN_AMOUNT:
                handleMinAmountResponse(data);
                break;
            case STARTING_PAYMENT:
                handleStartPaymentResponse(data);
                break;
            case POLLING_PAYMENT:
                handlePaymentResponse(data);
                break;
            case COMPLETING_PAYMENT:
                handleCompleteResponse(data);
                break;
            case CANCELLING_PAYMENT:
                handleCancelResponse(data);
                break;
            case REPORTING_SALE:
                handleSaleResponse(data);
                break;
            default:
                Log.d(TAG, "状态 " + currentState + " 下收到未处理数据: " + data);
                break;
        }
    }

    private void handleStatusResponse(String data) {
        if (data.length() != RESPONSE_LENGTH_STANDARD) return;
        handler.removeMessages(MSG_QUERY_STATUS);
        deviceVersion = data.substring(6, 8);
        deviceType = data.substring(8, 10);
        currencyCode = data.substring(10, 14);
        Log.i(TAG, "设备状态: version=" + deviceVersion
                + " type=" + deviceType + " currency=" + currencyCode);
        cycleQueryMinAmount();
    }

    private void handleMinAmountResponse(String data) {
        if (data.length() != RESPONSE_LENGTH_STANDARD) return;
        try {
            handler.removeMessages(MSG_QUERY_MIN_AMOUNT);
            int base = Integer.parseInt(data.substring(6, 10), 16);
            int decimals = Integer.parseInt(data.substring(10, 14), 16);
            minAmount = (float) (base / Math.pow(10, decimals));
            deviceReady = true;
            Log.i(TAG, "设备就绪: minAmount=" + minAmount);
            NayaxCallback cb = this.callback;
            if (cb != null) {
                cb.onDeviceReady(deviceVersion, deviceType, currencyCode, minAmount);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "最小面额解析失败", e);
            notifyError(ERROR_INVALID_RESPONSE, "最小面额响应解析失败");
        }
    }

    private void handleStartPaymentResponse(String data) {
        if (!RESP_START_PAYMENT_OK.equalsIgnoreCase(data)) return;
        Log.i(TAG, "收款已发起");
        NayaxCallback cb = this.callback;
        if (cb != null) {
            cb.onPaymentStarted();
        }
        handler.sendEmptyMessageDelayed(MSG_POLL_PAYMENT, PAYMENT_FIRST_POLL_MS);
        handler.sendEmptyMessageDelayed(MSG_PAYMENT_TIMEOUT, PAYMENT_TIMEOUT_MS);
    }

    private void handlePaymentResponse(String data) {
        if (data.length() != RESPONSE_LENGTH_STANDARD) return;
        try {
            String payType = data.substring(6, 8);
            int multiple = Integer.parseInt(data.substring(8, 14), 16);
            float actualAmount = multiple * minAmount;

            Log.i(TAG, "支付检测: multiple=" + multiple + " actual=" + actualAmount
                    + " requested=" + requestedAmount);

            if (Math.round(requestedAmount * 100) <= Math.round(actualAmount * 100)) {
                receivedAmount = requestedAmount;
                handler.removeMessages(MSG_POLL_PAYMENT);
                handler.removeMessages(MSG_PAYMENT_TIMEOUT);

                NayaxCallback cb = this.callback;
                if (cb != null) {
                    cb.onPaymentReceived(payType, actualAmount);
                }
                handler.sendEmptyMessageDelayed(MSG_COMPLETE_PAYMENT, COMPLETE_DELAY_MS);
            }
        } catch (NumberFormatException e) {
            Log.e(TAG, "收款金额解析失败", e);
            notifyError(ERROR_INVALID_RESPONSE, "收款金额响应解析失败");
        }
    }

    private void handleCompleteResponse(String data) {
        if (!RESP_COMPLETE_OK.equalsIgnoreCase(data)) return;
        paying = false;
        Log.i(TAG, "收款完成: " + receivedAmount);
        NayaxCallback cb = this.callback;
        if (cb != null) {
            cb.onPaymentCompleted(receivedAmount);
        }
    }

    private void handleCancelResponse(String data) {
        if (!RESP_CANCEL_OK.equalsIgnoreCase(data)) return;
        paying = false;
        handler.removeMessages(MSG_POLL_PAYMENT);
        handler.removeMessages(MSG_PAYMENT_TIMEOUT);
        handler.removeMessages(MSG_COMPLETE_PAYMENT);
        Log.i(TAG, "收款已取消");
        NayaxCallback cb = this.callback;
        if (cb != null) {
            cb.onPaymentCancelled();
        }
    }

    private void handleSaleResponse(String data) {
        if (!RESP_SALE_OK.equalsIgnoreCase(data)) return;
        Log.i(TAG, "售卖结果已确认");
        NayaxCallback cb = this.callback;
        if (cb != null) {
            cb.onSaleResult(true);
        }
    }

    // ==================== 内部工具 ====================

    private void sendCommand(String command) {
        if (TextUtils.isEmpty(command)) {
            Log.w(TAG, "指令为空，跳过发送");
            return;
        }
        if (serialUtil == null || !serialUtil.isConnected()) {
            Log.w(TAG, "串口未连接，无法发送: " + command);
            return;
        }
        Log.d(TAG, "发送指令: " + command);
        serialUtil.sendHex(command);
    }

    private void removeAllMessages() {
        handler.removeMessages(MSG_QUERY_STATUS);
        handler.removeMessages(MSG_QUERY_MIN_AMOUNT);
        handler.removeMessages(MSG_POLL_PAYMENT);
        handler.removeMessages(MSG_PAYMENT_TIMEOUT);
        handler.removeMessages(MSG_COMPLETE_PAYMENT);
        handler.removeMessages(MSG_RECONNECT);
    }

    private void notifyError(int code, String message) {
        Log.e(TAG, "错误[" + code + "]: " + message);
        NayaxCallback cb = this.callback;
        if (cb != null) {
            cb.onError(code, message);
        }
    }

    private void notifyConnectionChanged(boolean connected) {
        NayaxCallback cb = this.callback;
        if (cb != null) {
            cb.onConnectionChanged(connected);
        }
    }

    // ==================== Handler消息处理 ====================

    private void handleMsg(Message msg) {
        switch (msg.what) {
            case MSG_QUERY_STATUS:
                cycleQueryStatus();
                break;
            case MSG_QUERY_MIN_AMOUNT:
                cycleQueryMinAmount();
                break;
            case MSG_POLL_PAYMENT:
                cyclePollPayment();
                break;
            case MSG_PAYMENT_TIMEOUT:
                paying = false;
                handler.removeMessages(MSG_POLL_PAYMENT);
                notifyError(ERROR_PAYMENT_TIMEOUT, "收款超时");
                break;
            case MSG_COMPLETE_PAYMENT:
                paying = false;
                currentState = State.COMPLETING_PAYMENT;
                sendCommand(NayaxCommand.getCompleteMoney());
                break;
            case MSG_RECONNECT:
                openSerialPort();
                break;
        }
    }

    /**
     * 静态Handler，避免内存泄漏
     */
    private static class SafeHandler extends Handler {
        private final WeakReference<NayaxManager> ref;

        SafeHandler(NayaxManager manager) {
            super(Looper.getMainLooper());
            ref = new WeakReference<>(manager);
        }

        @Override
        public void handleMessage(Message msg) {
            NayaxManager manager = ref.get();
            if (manager != null) {
                manager.handleMsg(msg);
            }
        }
    }
}
