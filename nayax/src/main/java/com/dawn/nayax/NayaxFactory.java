package com.dawn.nayax;

import android.content.Context;
import android.content.Intent;

public class NayaxFactory {
    //单例模式
    private static NayaxFactory instance;
    private Context mContext;
    private NayaxFactory(Context context){
        this.mContext = context;
        context.startService(new Intent(context, NayaxService.class));
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

    /**
     * 开启串口
     */
    public void startPort(int port, boolean autoCheckStatus) {
        Intent intent = new Intent(NayaxService.RECEIVER_NAYAX);
        intent.putExtra("command", "start_port");
        intent.putExtra("port", port);
        intent.putExtra("auto_check_status", autoCheckStatus);
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
    public void startReceive(float money, float minMoney) {
        Intent intent = new Intent(NayaxService.RECEIVER_NAYAX);
        intent.putExtra("command", "start_money");
        intent.putExtra("money", money);
        intent.putExtra("min_money", minMoney);
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
