package com.dawn.libcardnayax;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.dawn.nayax.NayaxCallback;
import com.dawn.nayax.NayaxManager;

import java.util.Random;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        NayaxManager.getInstance().setCallback(new NayaxCallback() {
            @Override
            public void onDeviceReady(String version, String deviceType, String currencyCode, float minAmount) {
                Log.i(TAG, "设备就绪: version=" + version + " type=" + deviceType
                        + " currency=" + currencyCode + " minAmount=" + minAmount);
            }

            @Override
            public void onPaymentStarted() {
                Log.i(TAG, "收款已发起");
            }

            @Override
            public void onPaymentReceived(String payType, float amount) {
                Log.i(TAG, "收到付款: type=" + payType + " amount=" + amount);
            }

            @Override
            public void onPaymentCompleted(float amount) {
                Log.i(TAG, "收款完成: " + amount);
            }

            @Override
            public void onPaymentCancelled() {
                Log.i(TAG, "收款已取消");
            }

            @Override
            public void onSaleResult(boolean success) {
                Log.i(TAG, "售卖结果: " + success);
            }

            @Override
            public void onError(int errorCode, String message) {
                Log.e(TAG, "错误[" + errorCode + "]: " + message);
            }

            @Override
            public void onConnectionChanged(boolean connected) {
                Log.i(TAG, "连接状态: " + connected);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        NayaxManager.getInstance().release();
    }

    public void startPort(View view) {
        NayaxManager.getInstance().connect(3);
    }

    public void getDeviceStatus(View view) {
        NayaxManager.getInstance().queryDeviceInfo();
    }

    public void getMinMoney(View view) {
        // 最小面额在连接时自动查询，此处手动重新查询
        NayaxManager.getInstance().queryDeviceInfo();
    }

    public void startMoney(View view) {
        Random random = new Random();
        float randomFloat = 0.01f + random.nextFloat() * 2.0f;
        randomFloat = Math.round(randomFloat * 100) / 100.0f;
        Log.i(TAG, "发起收款: " + randomFloat);
        NayaxManager.getInstance().startPayment(randomFloat);
    }

    public void getMoneyResult(View view) {
        // 收款轮询现在自动进行
        Log.i(TAG, "收款状态: paying=" + NayaxManager.getInstance().isPaying());
    }

    public void getCompleteMoney(View view) {
        NayaxManager.getInstance().confirmPayment();
    }

    public void getCancelMoney(View view) {
        NayaxManager.getInstance().cancelPayment();
    }

    public void setSaleResult(View view) {
        NayaxManager.getInstance().reportSaleResult();
    }
}