package com.dawn.nayax;

/**
 * Nayax通讯指令构造器
 * <p>
 * 负责构建所有与Nayax设备通信的HEX指令，并附加CRC16-Modbus校验。
 * 同时提供响应数据的CRC校验验证功能。
 * </p>
 */
public final class NayaxCommand {

    private NayaxCommand() {
        // 工具类，禁止实例化
    }

    /**
     * 获取MDB外设状态命令
     */
    public static String getMDBCommand() {
        return appendCRC("E10300010002");
    }

    /**
     * 获取支持最小面额
     */
    public static String getMinMoney() {
        return appendCRC("E10300040002");
    }

    /**
     * 发起收款指令
     *
     * @param amount    收款金额
     * @param minAmount 最小面额（必须大于0）
     * @throws IllegalArgumentException 如果minAmount <= 0
     */
    public static String getStartMoney(float amount, float minAmount) {
        if (minAmount <= 0) {
            throw new IllegalArgumentException("最小面额必须大于0");
        }
        long multiple = Math.round(amount / minAmount);
        String command = "E1102004";    // 功能码
        String len = "0003";            // 数据长度
        String dataLen = "06";          // 数据字节长度
        String productNum = "0001";     // 商品编号（货道）
        String moneyStr = String.format("%08X", multiple);
        return appendCRC(command + len + dataLen + productNum + moneyStr);
    }

    /**
     * 完成收款指令
     */
    public static String getCompleteMoney() {
        return appendCRC("E10610010001");
    }

    /**
     * 取消收款指令
     */
    public static String getCancelMoney() {
        return appendCRC("E10610020001");
    }

    /**
     * 售卖结果指令（成功）
     */
    public static String setSaleResult() {
        return appendCRC("E10610030001");
    }

    /**
     * 获取收款金额
     */
    public static String getMoney() {
        return appendCRC("E10300030002");
    }

    /**
     * 验证响应数据的CRC校验
     *
     * @param response 完整的响应数据（含CRC尾部）
     * @return true 如果CRC校验通过
     */
    public static boolean validateResponse(String response) {
        if (response == null || response.length() < 6 || response.length() % 2 != 0) {
            return false;
        }
        String dataPart = response.substring(0, response.length() - 4);
        String crcPart = response.substring(response.length() - 4).toUpperCase();
        byte[] bytes = hexToBytes(dataPart);
        if (bytes == null) {
            return false;
        }
        String computed = computeCRC(bytes).toUpperCase();
        return crcPart.equals(computed);
    }

    /**
     * 为数据追加CRC16校验码
     *
     * @param data 不含CRC的十六进制数据字符串
     * @return 包含CRC的完整指令；数据无效时返回null
     */
    static String appendCRC(String data) {
        if (data == null) return null;
        data = data.replace(" ", "");
        if (data.isEmpty() || data.length() % 2 != 0) {
            return null;
        }
        byte[] bytes = hexToBytes(data);
        if (bytes == null) {
            return null;
        }
        return data + computeCRC(bytes);
    }

    /**
     * 计算CRC16校验码（Modbus标准，低位在前）
     *
     * @param bytes 字节数组
     * @return 校验码的4位十六进制字符串（低位在前高位在后）
     */
    static String computeCRC(byte[] bytes) {
        int crc = 0xFFFF;
        int polynomial = 0xA001;
        for (byte b : bytes) {
            crc ^= (b & 0xFF);
            for (int j = 0; j < 8; j++) {
                if ((crc & 0x0001) != 0) {
                    crc >>= 1;
                    crc ^= polynomial;
                } else {
                    crc >>= 1;
                }
            }
        }
        String result = String.format("%04X", crc & 0xFFFF);
        // 低位在前高位在后
        return result.substring(2, 4) + result.substring(0, 2);
    }

    /**
     * 十六进制字符串转字节数组
     *
     * @param hex 十六进制字符串（偶数长度）
     * @return 字节数组；格式无效时返回null
     */
    private static byte[] hexToBytes(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            return null;
        }
        int len = hex.length() / 2;
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            try {
                bytes[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return bytes;
    }
}
