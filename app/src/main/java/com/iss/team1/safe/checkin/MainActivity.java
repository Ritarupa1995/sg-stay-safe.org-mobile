package com.iss.team1.safe.checkin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKeys;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.iss.team1.safe.checkin.model.SafeResponse;
import com.iss.team1.safe.checkin.utils.HashUtil;
import com.iss.team1.safe.checkin.utils.HttpUtil;
import com.iss.team1.safe.checkin.utils.HttpsUtil;
import com.iss.team1.safe.checkin.utils.JsonUtil;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "IdTokenActivity";
    private static final int RC_GET_TOKEN = 9002;
    private static final String authURL = "https://checkin-sg-stay-safe-org.ap-southeast-1.elasticbeanstalk.com/authentication";
    private static final String authURL2 = "https://checkin.sg-stay-safe.com/authentication";

    private GoogleSignInClient mGoogleSignInClient;
    private TextView mIdTokenTextView;
    private Button mRefreshButton;

    private SharedPreferences.Editor sharedPrefsEditor;
    private SharedPreferences sharedPreferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Views
        mIdTokenTextView = findViewById(R.id.detail);
        mRefreshButton = findViewById(R.id.button_optional_action);
        mRefreshButton.setText(R.string.refresh_token);

        // Button click listeners
        findViewById(R.id.sign_in_button).setOnClickListener(this);
        findViewById(R.id.sign_out_button).setOnClickListener(this);
        findViewById(R.id.disconnect_button).setOnClickListener(this);
        mRefreshButton.setOnClickListener(this);

        // For sample only: make sure there is a valid server client ID.
        validateServerClientID();
        initSharedPreferences();

        // [START configure_signin]
        // Request only the user's ID token, which can be used to identify the
        // user securely to your backend. This will contain the user's basic
        // profile (name, profile picture URL, etc) so you should not need to
        // make an additional call to personalize your application.
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.server_client_id))
                .requestEmail()
                .build();
        // [END configure_signin]

        // Build GoogleAPIClient with the Google Sign-In API and the above options.
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);
    }

    private void getIdToken() {
        // Show an account picker to let the user choose a Google account from the device.
        // If the GoogleSignInOptions only asks for IDToken and/or profile and/or email then no
        // consent screen will be shown here.
        Intent signInIntent = mGoogleSignInClient.getSignInIntent();
        startActivityForResult(signInIntent, RC_GET_TOKEN);
    }

    private void signOut() {
        mGoogleSignInClient.signOut().addOnCompleteListener(this, new OnCompleteListener<Void>() {
            @Override
            public void onComplete(@NonNull Task<Void> task) {
                updateUI(null);
            }
        });
    }

    private void revokeAccess() {
        mGoogleSignInClient.revokeAccess().addOnCompleteListener(this,
                new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        updateUI(null);
                    }
                });
    }

    private void refreshIdToken() {
        // Attempt to silently refresh the GoogleSignInAccount. If the GoogleSignInAccount
        // already has a valid token this method may complete immediately.
        //
        // If the user has not previously signed in on this device or the sign-in has expired,
        // this asynchronous branch will attempt to sign in the user silently and get a valid
        // ID token. Cross-device single sign on will occur in this branch.
        mGoogleSignInClient.silentSignIn()
                .addOnCompleteListener(this, new OnCompleteListener<GoogleSignInAccount>() {
                    @Override
                    public void onComplete(@NonNull Task<GoogleSignInAccount> task) {
                        handleSignInResult(task);
                    }
                });
    }

    private void updateUI(@Nullable GoogleSignInAccount account) {
        if (account != null) {
            ((TextView) findViewById(R.id.status)).setText(R.string.signed_in);

            String idToken = account.getIdToken();
            mIdTokenTextView.setText(getString(R.string.id_token_fmt, idToken));

            findViewById(R.id.sign_in_button).setVisibility(View.GONE);
            findViewById(R.id.sign_out_and_disconnect).setVisibility(View.VISIBLE);
            mRefreshButton.setVisibility(View.VISIBLE);
        } else {
            ((TextView) findViewById(R.id.status)).setText(R.string.signed_out);
            mIdTokenTextView.setText(getString(R.string.id_token_fmt, "null"));

            findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            findViewById(R.id.sign_out_and_disconnect).setVisibility(View.GONE);
            mRefreshButton.setVisibility(View.GONE);
        }
    }

    private void handleSignInResult(@NonNull Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            String idToken = account.getIdToken();
            String email = account.getEmail();

            
            Log.i("get ID token: ", idToken);
//            SharedPreferences prefs = getSharedPreferences("safeStore", Context.MODE_PRIVATE);
//            SharedPreferences.Editor editor = prefs.edit();
//            editor.putString("idToken", idToken);
//            editor.commit();
            savePR(idToken);
            new MyTask().execute(email, idToken);
            updateUI(account);
            Intent intent = new Intent(this, HomePageActivity.class);
            startActivityForResult(intent, 1000);
        } catch (Exception e) {
            Log.w(TAG, "handleSignInResult:error", e);
            updateUI(null);
        }
    }

    /**
     * Validates that there is a reasonable server client ID in strings.xml, this is only needed
     * to make sure users of this sample follow the README.
     */
    private void validateServerClientID() {
        String serverClientId = getString(R.string.server_client_id);
        String suffix = ".apps.googleusercontent.com";
        if (!serverClientId.trim().endsWith(suffix)) {
            String message = "Invalid server client ID in strings.xml, must end with " + suffix;

            Log.w(TAG, message);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_GET_TOKEN) {
            // [START get_id_token]
            // This task is always completed immediately, there is no need to attach an
            // asynchronous listener.
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
            // [END get_id_token]
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.sign_in_button:
                getIdToken();
                break;
            case R.id.sign_out_button:
                signOut();
                break;
            case R.id.disconnect_button:
                revokeAccess();
                break;
            case R.id.button_optional_action:
                refreshIdToken();
                break;
        }
    }

    private class MyTask extends AsyncTask<String, Void, Void> {
        String resultMsg = "";
        @Override
        protected Void doInBackground(String... strings) {
            String email = strings[0];
            String idToken = strings[1];

            try {
                String hashEmail = HashUtil.hashSha256(email);
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("idToken", idToken);
                jsonObject.put("anonymousId", hashEmail);
                String respStr = HttpsUtil.jsonPostWithCA(authURL2, jsonObject.toString(), getApplication(), null);
                Log.i("Auth respStr", respStr);
                SafeResponse response = (SafeResponse) JsonUtil.convertJsonToObj(respStr, SafeResponse.class);
                if (response.getCode() != SafeResponse.RESPONSE_CODE_SUCCESS) {
                    resultMsg = response.getMsg();
                }
            } catch (Exception e) {
                Log.e("Auth api invoke error: ", e.toString());
                resultMsg = "Auth unsuccessfully...";
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            Context context = getApplicationContext();
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, resultMsg, duration);
            toast.show();
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

    private void savePR(String value) {
        sharedPrefsEditor.putString("safeStore", value);
        sharedPrefsEditor.apply();
    }
}
