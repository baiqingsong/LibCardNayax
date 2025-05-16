package com.dawn.nayax;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;

import com.dawn.serial.LSerialUtil;

public class NayaxService extends Service {
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    public static final String RECEIVER_NAYAX = "receiver_nayax";//纸钞广播
    private LSerialUtil serialUtil;
    private NayaxReceiver mReceiver;
    private enum card_status{
        status, //设备状态
        min_money, //最小金额
        start_money, //发起收款
        money, //收款金额
        complete_money, //完成收款
        cancel_money, //取消收款
        sale_result, //出钞结果
        common, //默认
    }
    private card_status currentStatus = card_status.status;

    private final static int h_status = 0x01; //设备状态
    private final static int h_min_money = 0x02; //最小金额
    private final static int h_money = 0x03; //完成收款
    private final static int h_money_delay = 0x04; //收款超时
    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case h_status://获取状态
                    cycleGetStatus();
                    break;
                case h_min_money://获取最小金额
                    cycleGetMinMoney();
                    break;
                case h_money://获取收款金额
                    cycleGetMoney();
                    break;
                case h_money_delay://收款超时
                    isPaying = false;
                    mHandler.removeMessages(h_money);
                    break;
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e("dawn", "NayaxService onCreate");
        registerReceiver();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if(mReceiver != null)
            unregisterReceiver(mReceiver);
    }

    private boolean autoCheckStatus = false;

    /**
     * 注册广播
     */
    private void registerReceiver() {
        mReceiver = new NayaxReceiver();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(RECEIVER_NAYAX);
        registerReceiver(mReceiver, intentFilter);
    }
    /**
     * 纸钞机广播
     */
    private class NayaxReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if(intent == null)
                return;
            String command = intent.getStringExtra("command");
            if(TextUtils.isEmpty(command))
                return;
            switch (command){
                case "start_port":
                    int port = intent.getIntExtra("port", 1);
                    autoCheckStatus = intent.getBooleanExtra("auto_check_status", false);
                    startPort(port);
                    break;
                case "get_device_status"://获取mdb设备状态
                    currentStatus = card_status.status;
                    sendMsg(NayaxCommand.getMDBCommand());//获取MDB设备状态
                    break;
                case "get_min_money"://获取支持最小面额
                    currentStatus = card_status.min_money;
                    sendMsg(NayaxCommand.getMinMoney());
                    break;
                case "start_money"://开始收款
                    if(isPaying)
                        return;
                    isPaying = true;
                    currentStatus = card_status.start_money;
                    float money = intent.getFloatExtra("money", 0);
                    float minMoney = intent.getFloatExtra("min_money", 0);
                    if(minMoney <= 0)
                        minMoney = localMinMoney;
                    startMoney = money;
                    sendMsg(NayaxCommand.getStartMoney(money, minMoney));//开始收款
                    break;
                case "get_money"://获取收款金额
                    currentStatus = card_status.money;
                    sendMsg(NayaxCommand.getMoney());//获取收款金额
                    break;
                case "get_cancel_money"://取消收款
                    currentStatus = card_status.cancel_money;
                    sendMsg(NayaxCommand.getCancelMoney());//取消收款
                    break;
                case "get_complete_money"://完成收款
                    currentStatus = card_status.complete_money;
                    sendMsg(NayaxCommand.getCompleteMoney());//完成收款
                    break;
                case "set_sale_result"://获取销售结果
                    currentStatus = card_status.sale_result;
                    sendMsg(NayaxCommand.setSaleResult());//获取销售结果
                    break;

            }

        }
    }
    private float localMinMoney = 0;//最小金额
    private float startMoney = 0;//开始金额
    private float receiverMoney = 0;//收款金额
    private boolean isPaying = false;//是否正在收款
    /**
     * 开启串口
     * @param port 串口号
     */
    private void startPort(int port){
        if(serialUtil == null){
            serialUtil = new LSerialUtil(port, 9600, LSerialUtil.SerialType.TYPE_HEX, new LSerialUtil.OnSerialListener() {
                @Override
                public void startError() {
                    Log.e("dawn", "串口打开失败");
                }

                @Override
                public void receiverError() {
                    Log.e("dawn", "串口接收失败");
                }

                @Override
                public void sendError() {
                    Log.e("dawn", "串口发送失败");
                }

                @Override
                public void getReceiverStr(String str) {
                    if(TextUtils.isEmpty(str))
                        return;
//                    Log.e("dawn", "receiver = " + str);
                    switch (currentStatus){
                        case status://查询状态返回结果
                            if(str.length() == 18){
                                mHandler.removeMessages(h_status);
                                String version = str.substring(6, 8);//控制板版本
                                String type = str.substring(8, 10);//外设机器种类
                                String moneyNo = str.substring(10, 14);//货币代码
                                cycleGetMinMoney();
                                NayaxReceiverListener listener = NayaxFactory.getInstance(NayaxService.this).getListener();
                                if(listener != null)
                                    listener.getDeviceStatus(version, type, moneyNo);
                            }
                            break;
                        case min_money://查询最小金额返回结果
                            if(str.length() == 18){
                                try{
                                    mHandler.removeMessages(h_min_money);
                                    int moneyBase = Integer.parseInt(str.substring(6, 10), 16);//货币基数
                                    int moneyPoint = Integer.parseInt(str.substring(10, 14), 16);//货币小数点
                                    float minMoney = (float) (moneyBase / Math.pow(10, moneyPoint));//最小金额
                                    localMinMoney = minMoney;
                                    NayaxReceiverListener listener = NayaxFactory.getInstance(NayaxService.this).getListener();
                                    if(listener != null)
                                        listener.getMinMoney(minMoney);
                                }catch (Exception e){
                                    e.printStackTrace();
                                }
                            }
                            break;
                        case start_money://开始收款
                            if("E11020040003DC69".equals(str)){
                                NayaxReceiverListener listener = NayaxFactory.getInstance(NayaxService.this).getListener();
                                if(listener != null)
                                    listener.getStartMoney();
                                mHandler.sendEmptyMessageDelayed(h_money, 3000);
                                mHandler.sendEmptyMessageDelayed(h_money_delay, 30 * 1000);
                            }
                            break;
                        case money://获取收款金额
                            if(str.length() == 18){
                                try{
                                    String type = str.substring(6, 8);//支付方式
                                    int multiple = Integer.parseInt(str.substring(8, 14), 16);//最小金额的倍数
                                    if(startMoney <= (multiple * localMinMoney)){
                                        receiverMoney = multiple * localMinMoney;
                                        NayaxReceiverListener listener = NayaxFactory.getInstance(NayaxService.this).getListener();
                                        if(listener != null)
                                            listener.getMoney(type, multiple * localMinMoney);
                                        Log.i("dawn", "multiple = " + multiple + "   localMinMoney = " + localMinMoney + "   startMoney = " + startMoney);
                                        // 收款和支付金额相同，完成收款
                                        currentStatus = card_status.complete_money;
                                        sendMsg(NayaxCommand.getCompleteMoney());//完成收款
                                    }
                                }catch (Exception e){
                                    e.printStackTrace();
                                }

                            }
                            break;
                        case complete_money://完成收款
                            if("E106100100010B6A".equals(str)){
                                NayaxReceiverListener listener = NayaxFactory.getInstance(NayaxService.this).getListener();
                                if(listener != null)
                                    listener.getCompleteMoney(receiverMoney);
                                isPaying = false;
                                mHandler.removeMessages(h_money);
                                mHandler.removeMessages(h_money_delay);
                            }
                            break;
                        case cancel_money://取消收款
                            if("E10610020001FB6A".equals(str)){
                                NayaxReceiverListener listener = NayaxFactory.getInstance(NayaxService.this).getListener();
                                if(listener != null)
                                    listener.getCancelMoney();
                                mHandler.removeMessages(h_money);
                                isPaying = false;
                                mHandler.removeMessages(h_money_delay);
                            }
                            break;
                        case sale_result://获取销售结果
                            if("E10610030001AAAA".equals(str)){
                                NayaxReceiverListener listener = NayaxFactory.getInstance(NayaxService.this).getListener();
                                if(listener != null)
                                    listener.getSaleResult(true);
                            }
                            break;
                    }
                }
            });
            if(autoCheckStatus){
                mHandler.sendEmptyMessageDelayed(h_status, 3000);
            }
        }
    }

    /**
     * 获取设备状态
     */
    private void cycleGetStatus(){
        if(autoCheckStatus){
            mHandler.removeMessages(h_status);
            currentStatus = card_status.status;
            sendMsg(NayaxCommand.getMDBCommand());//获取MDB设备状态
            mHandler.sendEmptyMessageDelayed(h_status, 5000);
        }
    }

    /**
     * 获取最小金额
     */
    private void cycleGetMinMoney(){
        mHandler.removeMessages(h_min_money);
        currentStatus = card_status.min_money;
        sendMsg(NayaxCommand.getMinMoney());
        mHandler.sendEmptyMessageDelayed(h_min_money, 5000);
    }

    /**
     * 获取收款信息
     */
    private void cycleGetMoney(){
        mHandler.removeMessages(h_money);
        currentStatus = card_status.money;
        sendMsg(NayaxCommand.getMoney());//获取收款金额
        mHandler.sendEmptyMessageDelayed(h_money, 2000);
    }

    /**
     * 发送信息
     */
    private void sendMsg(String msg) {
//        Log.e("dawn", "sendMsg = " + msg);
        if (serialUtil != null && !TextUtils.isEmpty(msg))
            serialUtil.sendHexMsg(msg);
    }
}
