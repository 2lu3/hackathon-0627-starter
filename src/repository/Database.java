package com.youtrust.hackathon.repository;

import com.youtrust.hackathon.domain.AuthProviderCredential;
import com.youtrust.hackathon.domain.User;

/**
 * データベース接続のモック。
 */
public class Database {
    public User findByEmail(String email) { return null; }
    public AuthProviderCredential findAuthProvider(String email, String provider) { return null; }
    public void save(User user) { user.setId("user_" + System.currentTimeMillis()); }
    public void saveAuthProvider(AuthProviderCredential credential) {
        System.out.println("Linked " + credential.getProvider() + " auth to: " + credential.getEmail());
    }
}
