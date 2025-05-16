package com.dawn.libcardnayax;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.dawn.nayax.NayaxCommand;
import com.dawn.nayax.NayaxFactory;
import com.dawn.nayax.NayaxReceiverListener;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        String crc = NayaxCommand.getStartMoney(1,  0.01f);
//        Log.e("dawn", "crc = " + crc);
        NayaxFactory.getInstance(this).setListener(new NayaxReceiverListener() {
            @Override
            public void getDeviceStatus(String version, String type, String moneyNo) {
                Log.e("dawn", "getDeviceStatus version = " + version + ", type = " + type + ", moneyNo = " + moneyNo);
            }

            @Override
            public void getMinMoney(float minMoney) {
                Log.e("dawn", "getMinMoney minMoney = " + minMoney);
            }

            @Override
            public void getStartMoney() {
                Log.e("dawn", "getStartMoney");
            }

            @Override
            public void getMoney(String type, float multiple) {
                Log.e("dawn", "getMoney type = " + type + ", multiple = " + multiple);
            }

            @Override
            public void getCompleteMoney(float receiverMoney) {
                Log.e("dawn", "getCompleteMoney");
            }

            @Override
            public void getCancelMoney() {
                Log.e("dawn", "getCancelMoney");
            }

            @Override
            public void getSaleResult(boolean isSuccess) {
                Log.e("dawn", "getSaleResult isSuccess = " + isSuccess);
            }
        });
    }

    public void startPort(View view){
        NayaxFactory.getInstance(this).startPort(3, true);
    }

    public void getDeviceStatus(View view){
        NayaxFactory.getInstance(this).getDeviceStatus();
    }

    public void getMinMoney(View view){
        NayaxFactory.getInstance(this).getMinMoney();
    }

    public void startMoney(View view){
        NayaxFactory.getInstance(this).startReceive(0.02f, 0);
    }

    public void getMoneyResult(View view){
        NayaxFactory.getInstance(this).getMoney();
    }

    public void getCompleteMoney(View view){
        NayaxFactory.getInstance(this).getCompleteMoney();
    }

    public void getCancelMoney(View view){
        NayaxFactory.getInstance(this).getCancelMoney();
    }

    public void setSaleResult(View view){
        NayaxFactory.getInstance(this).setSaleResult();
    }
}