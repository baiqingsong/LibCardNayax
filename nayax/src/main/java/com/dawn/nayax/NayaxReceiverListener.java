package com.dawn.nayax;

/**
 * 回调接口
 */
public interface NayaxReceiverListener {
    void getDeviceStatus(String version, String type, String moneyNo);//获取设备状态,版本号,设备类型,货币代码
    void getMinMoney(float minMoney);//获取最小金额
    void getStartMoney();//开始收款
    void getMoney(String type, int multiple);//获取收款金额(支付方式,最小金额的倍数)
    void getCompleteMoney();//完成收款
    void getCancelMoney();//取消收款
    void getSaleResult(boolean isSuccess);//售卖结果
}
