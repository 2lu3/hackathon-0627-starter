package com.youtrust.hackathon.application;

/**
 * パスワード登録の入力 DTO。
 */
public class RegisterInput {
    private String email;
    private String password;
    private String name;

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
