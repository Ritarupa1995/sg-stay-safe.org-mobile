package com.iss.team1.safe.checkin;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import com.iss.team1.safe.checkin.model.CheckInRequestVo;
import com.iss.team1.safe.checkin.model.QRStr;
import com.iss.team1.safe.checkin.model.ResponseVo;
import com.iss.team1.safe.checkin.utils.HashUtil;
import com.iss.team1.safe.checkin.utils.HttpsUtil;
import com.iss.team1.safe.checkin.utils.JsonUtil;

public class HomePageActivity extends AppCompatActivity {
    private final int REQUEST_CODE_ADDRESS = 100;
    private final String[] permissions = new String[]{Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE};
    private String checkInUrl = "https://checkin-sg-stay-safe-org.ap-southeast-1.elasticbeanstalk.com/";
    private String checkInUrl2 = "https://checkin.sg-stay-safe.com/";
    private TextView tvCheckinReqult;

    private SharedPreferences.Editor sharedPrefsEditor;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_page);

        tvCheckinReqult = findViewById(R.id.tv_checkin_result);
        findViewById(R.id.tv_code).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                for (int i = 0; i < permissions.length; i++) {
                    if (ContextCompat.checkSelfPermission(HomePageActivity.this, permissions[0]) != PackageManager.PERMISSION_GRANTED
                            || ContextCompat.checkSelfPermission(HomePageActivity.this, permissions[1]) != PackageManager.PERMISSION_GRANTED
                            || ContextCompat.checkSelfPermission(HomePageActivity.this, permissions[2]) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions(HomePageActivity.this, permissions, REQUEST_CODE_ADDRESS);
                    } else {
                        Intent intent = new Intent(HomePageActivity.this, ScanningQRCodeActivity.class);
                        startActivityForResult(intent, 1000);
                        break;
                    }
                }
            }
        });
        initSharedPreferences();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1000 && resultCode == Activity.RESULT_OK) {
            if (data != null) {
                String resultText = data.getStringExtra("resultText");

              
                new MyTask().execute(resultText);

            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_ADDRESS) {
            for (int i = 0; i < permissions.length; i++) {
                if (permissions[i].equals(this.permissions)) {
                    Intent intent = new Intent(HomePageActivity.this, ScanningQRCodeActivity.class);
                    startActivity(intent);
                    break;
                }
            }
        }
    }

    private class MyTask extends AsyncTask<String, Void, Void> {
        String resultMsg = "";

        @Override
        protected Void doInBackground(String... strings) {
            String rawStr = strings[0];

            Log.i("checkin rawStr", rawStr);
            String jsonStr = HashUtil.deCrypt(rawStr);
            Log.i("checkin jsonStr", jsonStr);
            QRStr qrObj = (QRStr) JsonUtil.convertJsonToObj(jsonStr, QRStr.class);
            if (qrObj == null || TextUtils.isEmpty(qrObj.getSiteId())) {
                resultMsg = "QR Code is invalid...";
            }
            CheckInRequestVo requestVo = new CheckInRequestVo("", qrObj.getSiteId());
            String requestStr = JsonUtil.convertObjToStr(requestVo);
            Log.i("checkin requestStr", requestStr);
            String respStr = null;
            try {
//                SharedPreferences prefs = getSharedPreferences("safeStore", Context.MODE_PRIVATE);
//                String idToken = prefs.getString("idToken", null);
                String idToken = getPR("safeStore");
                Log.i("SharedPreferences idToken={}", idToken);
                respStr = HttpsUtil.jsonPostWithCA(checkInUrl2, requestStr, getApplication(), idToken);
                Log.i("checkin respStr", respStr);
            } catch (Exception e) {
                e.printStackTrace();
                resultMsg = "Check in unsuccessfully...";
            }
            if (TextUtils.isEmpty(respStr)) {
                resultMsg = "Check in unsuccessfully...";
            } else {
                ResponseVo response = (ResponseVo) JsonUtil.convertJsonToObj(respStr, ResponseVo.class);
                resultMsg = response.getMsg();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            tvCheckinReqult.setText(resultMsg);
            tvCheckinReqult.getPaint().setFakeBoldText(true);
        }
    }

    private void initSharedPreferences() {
        String sharedPrefsFile = "ID_TOKEN";
        KeyGenParameterSpec keyGenParameterSpec = MasterKeys.AES256_GCM_SPEC;
        String mainKeyAlias = null;
        try {
            mainKeyAlias = MasterKeys.getOrCreate(keyGenParameterSpec);
            sharedPreferences = EncryptedSharedPreferences.create(
                    sharedPrefsFile,
                    mainKeyAlias,
                    getApplicationContext(),
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            );

            sharedPrefsEditor = sharedPreferences.edit();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String getPR(String keyValue) {
        String prValue = sharedPreferences.getString(keyValue, null);
        return prValue;
    }
}
