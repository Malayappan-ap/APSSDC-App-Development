package com.trader.stockadvisorai;

public class User {
    public String name, email;

    public User() {} // Required for Firebase

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }
}
