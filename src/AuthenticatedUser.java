package com.youtrust.hackathon;

/**
 * 認証成功後の正規化された本人情報。
 *
 * <p>パスワード認証、OAuth 認証など入口ごとの違いを吸収し、
 * 登録パイプラインへ渡すための domain model。
 */
public class AuthenticatedUser {
    private final String email;
    private final String name;
    private final String provider;     // "password" / "github" / ...
    private final String passwordHash; // パスワード登録のみ

    public AuthenticatedUser(String email, String name, String provider, String passwordHash) {
        this.email = email;
        this.name = name;
        this.provider = provider;
        this.passwordHash = passwordHash;
    }

    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getProvider() { return provider; }
    public String getPasswordHash() { return passwordHash; }
}
