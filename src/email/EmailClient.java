package com.youtrust.hackathon.email;

/**
 * メール送信のモック。
 */
public class EmailClient {
    public void send(String to, String subject, String body) {
        System.out.println("Email sent to: " + to);
    }
}
