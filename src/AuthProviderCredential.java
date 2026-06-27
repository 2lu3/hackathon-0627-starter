package com.youtrust.hackathon;

/**
 * email を正として、ユーザーに連携された認証プロバイダを表す domain model。
 */
public class AuthProviderCredential {
    private final String email;
    private final String provider;

    public AuthProviderCredential(String email, String provider) {
        this.email = email;
        this.provider = provider;
    }

    public String getEmail() { return email; }
    public String getProvider() { return provider; }
}
