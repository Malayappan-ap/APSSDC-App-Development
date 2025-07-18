package com.trader.stockadvisorai;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class ResultDashboardActivity extends AppCompatActivity {

    private LinearLayout resultLayout;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result_dashboard);

        resultLayout = findViewById(R.id.resultLayout);
        db = FirebaseFirestore.getInstance();

        fetchPredictionData();
        Button goToProfile = findViewById(R.id.goToProfileButton);
        goToProfile.setOnClickListener(v -> {
            Intent intent = new Intent(ResultDashboardActivity.this, ProfileActivity.class);
            startActivity(intent);
        });


    }

    private void fetchPredictionData() {
        String responseData = getIntent().getStringExtra("result");

        if (responseData == null) {
            showError("No data received");
            return;
        }

        try {
            JSONObject jsonResponse = new JSONObject(responseData);
            JSONArray data = jsonResponse.getJSONArray("stocks");

            for (int i = 0; i < data.length(); i++) {
                JSONObject stock = data.getJSONObject(i);

                String symbol = stock.getString("symbol");
                double current = stock.getDouble("current_price");
                int quantity = stock.has("quantity") ? stock.getInt("quantity") : 0;
                double invested = stock.has("invested") ? stock.getDouble("invested") : 0.0;
                double yesterday = stock.has("yesterday_close") ? stock.getDouble("yesterday_close") : 0.0;
                double predicted = stock.has("predicted_price") ? stock.getDouble("predicted_price") : 0.0;
                String advice = stock.has("advice") ? stock.getString("advice") : "N/A";

                // 📊 Build UI display
                StringBuilder info = new StringBuilder();
                info.append("📊 ").append(symbol);
                info.append("\nCurrent: ₹").append(current);
                if (quantity > 0) info.append("\nSuggested Quantity: ").append(quantity);
                if (invested > 0) info.append("\nInvested: ₹").append(invested);
                if (yesterday > 0) info.append("\nYesterday: ₹").append(yesterday);
                if (predicted > 0) info.append("\nPredicted: ₹").append(predicted);
                if (!advice.equals("N/A")) info.append("\nAdvice: ").append(advice);

                TextView tv = new TextView(this);
                tv.setText(info.toString());
                tv.setTextSize(16f);
                tv.setTextColor(Color.WHITE);
                tv.setBackgroundColor(Color.DKGRAY);
                tv.setPadding(24, 24, 24, 24);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                );
                params.setMargins(0, 0, 0, 24);
                tv.setLayoutParams(params);

                resultLayout.addView(tv);


                saveToFirestore(symbol, current, quantity, invested, yesterday, predicted, advice);
            }

        } catch (Exception e) {
            e.printStackTrace();
            showError("Failed to parse response");
        }
    }

    private void saveToFirestore(String symbol, double currentPrice, int quantity, double invested,
                                 double yesterday, double predicted, String advice) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("symbol", symbol);
        entry.put("current_price", currentPrice);
        entry.put("quantity", quantity);
        entry.put("invested", invested);
        entry.put("yesterday_close", yesterday);
        entry.put("predicted_price", predicted);
        entry.put("advice", advice);
        entry.put("timestamp", System.currentTimeMillis());

        db.collection("prediction_history")
                .add(entry)
                .addOnSuccessListener(docRef -> Log.d("FIRESTORE", "Saved: " + symbol))
                .addOnFailureListener(e -> Log.e("FIRESTORE", "Failed to save", e));
    }

    private void showError(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    // 🔄 Optional: Load past history from Firestore

    private void loadPredictionHistory() {
        db.collection("prediction_history")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .addOnSuccessListener(queryDocumentSnapshots -> {
                for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                    String symbol = doc.getString("symbol");
                    Double price = doc.getDouble("current_price");
                    String advice = doc.getString("advice");
                    Double predicted = doc.getDouble("predicted_price");

                    StringBuilder history = new StringBuilder();
                    history.append("📊 ").append(symbol);
                    history.append("\nCurrent: ₹").append(price);
                    if (predicted != null)
                        history.append("\nPredicted: ₹").append(predicted);
                    if (advice != null)
                        history.append("\nAdvice: ").append(advice);

                    TextView tv = new TextView(this);
                    tv.setText(history.toString());
                    tv.setTextSize(14f);
                    tv.setTextColor(Color.LTGRAY);
                    tv.setPadding(16, 16, 16, 16);
                    tv.setBackgroundColor(Color.GRAY);
                    resultLayout.addView(tv);
                }
            });


    }

}
