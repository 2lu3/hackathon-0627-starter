package com.youtrust.hackathon.oauth;

/**
 * OAuth 認証（認可コードの交換・プロフィール取得など）に失敗したことを表す例外。
 *
 * バリデーションエラー（IllegalArgumentException）と区別するために専用の型を用意し、
 * 呼び出し側が「外部プロバイダ起因の失敗」をハンドリングできるようにする。
 */
public class OAuthException extends Exception {

    private static final long serialVersionUID = 1L;

    public OAuthException(String message) {
        super(message);
    }

    public OAuthException(String message, Throwable cause) {
        super(message, cause);
    }
}
