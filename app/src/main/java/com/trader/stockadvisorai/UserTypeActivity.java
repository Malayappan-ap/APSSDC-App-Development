package com.trader.stockadvisorai;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class UserTypeActivity extends AppCompatActivity {

    private Button investorBtn, traderBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_type);

        investorBtn = findViewById(R.id.investorBtn);
        traderBtn = findViewById(R.id.traderBtn);

        investorBtn.setOnClickListener(v -> {
            startActivity(new Intent(UserTypeActivity.this, UploadPortfolioActivity.class));
        });

        traderBtn.setOnClickListener(v -> {
            startActivity(new Intent(UserTypeActivity.this, EnterAmountActivity.class));
        });
    }
}
