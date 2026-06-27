package com.youtrust.hackathon.oauth.github;

import com.youtrust.hackathon.oauth.OAuthException;
import com.youtrust.hackathon.oauth.OAuthProvider;

/**
 * GitHub OAuth による認証の実装。
 *
 * <p>実際の流れは次の 2 ステップ。本ハッカソンでは HTTP 通信部分はモックにしているが、
 * 本番で差し替える箇所をコメントで明示している。
 * <ol>
 *   <li>認可コード → アクセストークン交換
 *       （POST https://github.com/login/oauth/access_token）</li>
 *   <li>アクセストークン → プロフィール取得
 *       （GET https://api.github.com/user, GET https://api.github.com/user/emails）</li>
 * </ol>
 *
 * <p>client_id / client_secret はハードコードせず、環境変数や設定から注入する。
 */
public class GitHubOAuthProvider implements OAuthProvider {

    private static final String PROVIDER_ID = "github";

    private final String clientId;
    private final String clientSecret;

    public GitHubOAuthProvider(String clientId, String clientSecret) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public OAuthUserInfo fetchUserInfo(String authorizationCode) throws OAuthException {
        if (authorizationCode == null || authorizationCode.trim().isEmpty()) {
            throw new OAuthException("認可コードが空です");
        }

        String accessToken = exchangeCodeForToken(authorizationCode);
        return fetchProfile(accessToken);
    }

    /**
     * 認可コードをアクセストークンに交換する。
     *
     * <p>本番実装イメージ:
     * <pre>
     *   POST https://github.com/login/oauth/access_token
     *   body: client_id, client_secret, code
     *   header: Accept: application/json
     *   → レスポンスの access_token を取り出す
     * </pre>
     */
    private String exchangeCodeForToken(String authorizationCode) throws OAuthException {
        if (clientId == null || clientSecret == null) {
            throw new OAuthException("GitHub OAuth のクライアント設定が未構成です");
        }
        // TODO: 実 HTTP 通信に差し替える。ここではモックトークンを返す。
        return "mock_github_access_token_for_" + authorizationCode;
    }

    /**
     * アクセストークンでユーザープロフィールを取得し、共通表現に正規化する。
     *
     * <p>本番実装イメージ:
     * <pre>
     *   GET https://api.github.com/user           → login(name), email
     *   GET https://api.github.com/user/emails    → primary かつ verified なメールを採用
     *                                                （GitHub はメール非公開のことがあるため）
     * </pre>
     */
    private OAuthUserInfo fetchProfile(String accessToken) throws OAuthException {
        if (accessToken == null || accessToken.isEmpty()) {
            throw new OAuthException("アクセストークンの取得に失敗しました");
        }
        // TODO: 実 HTTP 通信に差し替える。ここではモックのプロフィールを返す。
        String email = "octocat@example.com";
        String name = "The Octocat";

        if (email == null || email.isEmpty()) {
            // GitHub でメールが取得できないケース。本番では追加同意フローやエラー表示を検討する。
            throw new OAuthException("GitHub からメールアドレスを取得できませんでした");
        }
        return new OAuthUserInfo(email, name);
    }
}
