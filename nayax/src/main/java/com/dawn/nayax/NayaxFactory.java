package com.dawn.nayax;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;

public class NayaxFactory {
    //单例模式
    private static NayaxFactory instance;
    private Context mContext;
    private NayaxFactory(Context context){
        this.mContext = context;
    }
    public static NayaxFactory getInstance(Context context){
        if(instance == null){
            synchronized (NayaxFactory.class){
                if(instance == null){
                    instance = new NayaxFactory(context);
                }
            }
        }
        return instance;
    }
    private NayaxReceiverListener mNayaxReceiverListener;
    public NayaxReceiverListener getListener() {
        return mNayaxReceiverListener;
    }
    public void setListener(NayaxReceiverListener listener) {
        this.mNayaxReceiverListener = listener;
    }

    private Handler mHandler = new Handler(){
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what){
                case 0:
                    startPort();
                    break;
            }
        }
    };
    private int serialPort = 0;//串口号

    /**
     * 开启服务
     * @param port 串口号
     */
    public void startService(int port){
        serialPort = port;
        mContext.startService(new Intent(mContext, NayaxService.class));
        mHandler.sendEmptyMessageDelayed(0, 5000);
    }

    /**
     * 开启串口
     */
    public void startPort() {
        Intent intent = new Intent(NayaxService.RECEIVER_NAYAX);
        intent.putExtra("command", "start_port");
        intent.putExtra("port", serialPort);
        mContext.sendBroadcast(intent);
    }

    /**
     * 获取设备状态
     */
    public void getDeviceStatus() {
        Intent intent = new Intent(NayaxService.RECEIVER_NAYAX);
        intent.putExtra("command", "get_device_status");
        mContext.sendBroadcast(intent);
    }

    /**
     * 获取最小金额
     */
    public void getMinMoney(){
        Intent intent = new Intent(NayaxService.RECEIVER_NAYAX);
        intent.putExtra("command", "get_min_money");
        mContext.sendBroadcast(intent);
    }

    /**
     * 发起收款
     */
    public void startReceive(float money) {
        Intent intent = new Intent(NayaxService.RECEIVER_NAYAX);
        intent.putExtra("command", "start_money");
        intent.putExtra("money", money);
        mContext.sendBroadcast(intent);
    }

    /**
     * 获取收款金额
     */
    public void getMoney(){
        Intent intent = new Intent(NayaxService.RECEIVER_NAYAX);
        intent.putExtra("command", "get_money");
        mContext.sendBroadcast(intent);
    }

    /**
     * 取消收款
     */
    public void getCancelMoney(){
        Intent intent = new Intent(NayaxService.RECEIVER_NAYAX);
        intent.putExtra("command", "get_cancel_money");
        mContext.sendBroadcast(intent);
    }

    /**
     * 完成收款
     */
    public void getCompleteMoney() {
        Intent intent = new Intent(NayaxService.RECEIVER_NAYAX);
        intent.putExtra("command", "get_complete_money");
        mContext.sendBroadcast(intent);
    }

    /**
     * 获取售卖结果
     */
    public void setSaleResult() {
        Intent intent = new Intent(NayaxService.RECEIVER_NAYAX);
        intent.putExtra("command", "set_sale_result");
        mContext.sendBroadcast(intent);
    }
}
