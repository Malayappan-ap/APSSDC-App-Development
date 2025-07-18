package com.trader.stockadvisorai;

import android.content.Intent;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.util.concurrent.TimeUnit;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class EnterAmountActivity extends AppCompatActivity {

    private EditText amountInput;
    private Button getSuggestionsBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_enter_amount);

        amountInput = findViewById(R.id.amountInput);
        getSuggestionsBtn = findViewById(R.id.getSuggestionsBtn);

        getSuggestionsBtn.setOnClickListener(v -> {
            String amountStr = amountInput.getText().toString().trim();
            if (!amountStr.isEmpty()) {
                double amount = Double.parseDouble(amountStr);
                sendAmountToFlask(amount);
            } else {
                Toast.makeText(this, "Please enter an amount", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void sendAmountToFlask(double amount) {
        OkHttpClient client = new OkHttpClient.Builder()
                .callTimeout(15, TimeUnit.SECONDS)
                .build();

        JSONObject json = new JSONObject();
        try {
            json.put("amount", amount);
        } catch (JSONException e) {
            e.printStackTrace();
            runOnUiThread(() -> Toast.makeText(this, "Failed to build request", Toast.LENGTH_SHORT).show());
            return;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.parse("application/json"));

        Request request = new Request.Builder()
                .url("http://192.168.0.103:5000/suggest-stocks")
                .post(body)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() ->
                        Toast.makeText(EnterAmountActivity.this, "Request failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseData = response.body().string();
                    Log.d("FLASK_RESPONSE", responseData);

                    Intent intent = new Intent(EnterAmountActivity.this, ResultDashboardActivity.class);
                    intent.putExtra("result", responseData);
                    startActivity(intent);
                } else {
                    runOnUiThread(() -> Toast.makeText(EnterAmountActivity.this, "Invalid response", Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

}
