package com.trader.stockadvisorai;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class ProfileActivity extends AppCompatActivity {

    private TextView emailText, nameText;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        emailText = findViewById(R.id.emailTextView);
        nameText = findViewById(R.id.nameTextView);

        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        FirebaseUser user = mAuth.getCurrentUser();

        if (user != null) {
            // Set email
            emailText.setText("📧 " + user.getEmail());

            // Try getting display name from Firebase Auth
            if (user.getDisplayName() != null) {
                nameText.setText("👤 " + user.getDisplayName());
            } else {
                // Fallback: fetch name from Firestore using UID
                db.collection("users").document(user.getUid())
                        .get()
                        .addOnSuccessListener(documentSnapshot -> {
                            if (documentSnapshot.exists()) {
                                String name = documentSnapshot.getString("name");
                                nameText.setText("👤 " + (name != null ? name : "Padmini"));
                            } else {
                                nameText.setText("👤 Padmini");
                            }
                        })
                        .addOnFailureListener(e -> {
                            Log.e("ProfileActivity", "Error fetching name", e);
                            nameText.setText("👤 Padmini");
                        });
            }

        } else {
            emailText.setText("Not logged in");
            nameText.setText("");
        }
    }
}
