package com.dawn.nayax;

import android.util.Log;

public class NayaxCommand {
    /**
     * 获取MDB外设命令
     */
    public static String getMDBCommand(){
        return getCRC("E10300010002");
    }

    /**
     * 获取支持最小面额
     */
    public static String getMinMoney(){
        return getCRC("E10300040002");
    }

    /**
     * 发起收款指令
     * @param money 金额
     */
    public static String getStartMoney(float money, float minMoney){
        String command = "E1102004";//功能码
        String len = "0003";//数据长度
        String dataLen = "06";//数据字节长度
        String productNum = "0001";//商品编号，货道
        String moneyStr = String.format("%08X", (int) (money/minMoney));//金额
//        Log.e("dawn", "moneyStr = " + moneyStr + "   " + money + " " + minMoney + "    " + (money/minMoney));
        return getCRC(command + len + dataLen + productNum + moneyStr);
    }

    /**
     * 完成收款指令
     */
    public static String getCompleteMoney(){
        return getCRC("E10610010001");
    }

    /**
     * 取消收款指令
     */
    public static String getCancelMoney(){
        return getCRC("E10610020001");
    }

    /**
     * 售卖结果
     * 成功0001
     * 失败0000
     */
    public static String setSaleResult(){
        return getCRC("E10610030001");
    }

    /**
     * 获取收款金额
     */
    public static String getMoney(){
        return getCRC("E10300030002");
    }



    public static String getCRC(String data) {
        data = data.replace(" ", "");
        int len = data.length();
        if (!(len % 2 == 0)) {
            return "0000";
        }
        int num = len / 2;
        byte[] para = new byte[num];
        for (int i = 0; i < num; i++) {
            int value = Integer.valueOf(data.substring(i * 2, 2 * (i + 1)), 16);
            para[i] = (byte) value;
        }
        return data + getCRC(para);
    }

    /**
     * 计算CRC16校验码
     *
     * @param bytes 字节数组
     * @return {@link String} 校验码
     * @since 1.0
     */
    public static String getCRC(byte[] bytes) {
        // CRC寄存器全为1
        int CRC = 0x0000ffff;
        // 多项式校验值
        int POLYNOMIAL = 0x0000a001;
        int i, j;
        for (i = 0; i < bytes.length; i++) {
            CRC ^= ((int) bytes[i] & 0x000000ff);
            for (j = 0; j < 8; j++) {
                if ((CRC & 0x00000001) != 0) {
                    CRC >>= 1;
                    CRC ^= POLYNOMIAL;
                } else {
                    CRC >>= 1;
                }
            }
        }
        // 结果转换为16进制
        String result = Integer.toHexString(CRC).toUpperCase();
        if (result.length() != 4) {
            StringBuffer sb = new StringBuffer("0000");
            result = sb.replace(4 - result.length(), 4, result).toString();
        }
        //高位在前地位在后
        //return result.substring(2, 4) + " " + result.substring(0, 2);
        // 交换高低位，低位在前高位在后
        return result.substring(2, 4) + "" + result.substring(0, 2);
    }
}
