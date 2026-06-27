package com.youtrust.hackathon.service;

import com.youtrust.hackathon.application.RegisterInput;
import com.youtrust.hackathon.application.RegisterResult;
import com.youtrust.hackathon.domain.AuthProviderCredential;
import com.youtrust.hackathon.domain.AuthenticatedUser;
import com.youtrust.hackathon.domain.User;
import com.youtrust.hackathon.email.EmailClient;
import com.youtrust.hackathon.oauth.OAuthException;
import com.youtrust.hackathon.oauth.OAuthProvider;
import com.youtrust.hackathon.oauth.github.GitHubOAuthProvider;
import com.youtrust.hackathon.repository.Database;
import com.youtrust.hackathon.repository.UserDBResistory;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * ユーザー登録サービス。
 *
 * <p>登録の「入口」は認証方法ごとに分かれる（パスワード / GitHub OAuth / 将来の Google・LINE）が、
 * 認証が成功したらすべて {@link AuthenticatedUser}（正規化済みの本人情報）に変換され、
 * 共通の登録パイプライン {@link #register(AuthenticatedUser)} に合流する。
 *
 * <p>これにより「重複チェック・DB 保存・ウェルカムメール・ログ記録」という後続処理を 1 か所に集約でき、
 * どの認証方法でも同じ後続処理を必ず通ることを構造的に保証している。
 *
 * <p>OAuth プロバイダは {@link #registerOAuthProvider(OAuthProvider)} で差し込む。
 * ベンダー追加時に本クラスの変更は不要。
 */
public class UserRegistrationService {

    private static final Logger logger = Logger.getLogger(UserRegistrationService.class.getName());

    private static final String PROVIDER_PASSWORD = "password";

    private final Database database;
    private final UserDBResistory userDBResistory;
    private final EmailClient emailClient;

    /** providerId -> OAuthProvider。登録済みのベンダーを保持する。 */
    private final Map<String, OAuthProvider> oauthProviders = new HashMap<>();

    public UserRegistrationService() {
        this(new Database(), new EmailClient());
    }

    /** 依存を注入するコンストラクタ（テスト時にモックを差し込めるようにする）。 */
    public UserRegistrationService(Database database, EmailClient emailClient) {
        this(database, emailClient, new UserDBResistory(database));
    }

    public UserRegistrationService(Database database, EmailClient emailClient, UserDBResistory userDBResistory) {
        this.database = database;
        this.emailClient = emailClient;
        this.userDBResistory = userDBResistory;
    }

    /** OAuth プロバイダを登録する（例: GitHubOAuthProvider）。 */
    public void registerOAuthProvider(OAuthProvider provider) {
        oauthProviders.put(provider.providerId(), provider);
    }

    // ====================================================================
    // 入口 1: パスワードによる登録
    // ====================================================================
    public RegisterResult registerWithPassword(RegisterInput input) {
        validatePasswordInput(input);

        AuthenticatedUser auth = new AuthenticatedUser(
                input.getEmail(),
                input.getName(),
                PROVIDER_PASSWORD,
                hashPassword(input.getPassword()));

        return register(auth);
    }

    // ====================================================================
    // 入口 2: OAuth による登録（GitHub / 将来の Google・LINE）
    // ====================================================================
    public RegisterResult registerWithOAuth(String providerId, String authorizationCode) {
        OAuthProvider provider = oauthProviders.get(providerId);
        if (provider == null) {
            throw new IllegalArgumentException("未対応の認証プロバイダです: " + providerId);
        }

        OAuthProvider.OAuthUserInfo info;
        try {
            info = provider.fetchUserInfo(authorizationCode);
        } catch (OAuthException e) {
            // 外部プロバイダ起因の失敗。呼び出し側で扱いやすいよう実行時例外に変換する。
            throw new IllegalStateException("OAuth 認証に失敗しました: " + e.getMessage(), e);
        }

        validateOAuthInfo(info);

        AuthenticatedUser auth = new AuthenticatedUser(
                info.getEmail(),
                info.getName(),
                provider.providerId(),
                null);                         // パスワードなし

        return register(auth);
    }

    // ====================================================================
    // 共通登録パイプライン: すべての入口が必ずここを通る
    // ====================================================================
    private RegisterResult register(AuthenticatedUser auth) {
        User existingUser = database.findByEmail(auth.getEmail());
        if (existingUser != null && PROVIDER_PASSWORD.equals(auth.getProvider())) {
            throw new IllegalArgumentException("このメールアドレスはすでに登録されています");
        }

        if (existingUser != null) {
            linkAuthProvider(existingUser, auth.getProvider());
            logger.info("認証プロバイダ連携完了: " + existingUser.getEmail() + " (provider=" + auth.getProvider() + ")");
            return new RegisterResult(true, existingUser.getId(), "認証プロバイダを連携しました");
        }

        User user = userDBResistory.save(auth);

        if (!PROVIDER_PASSWORD.equals(auth.getProvider())) {
            linkAuthProvider(user, auth.getProvider());
        }

        // ウェルカムメール送信（全方法共通）
        sendWelcomeEmail(user);

        // ログ記録（全方法共通）
        logger.info("ユーザー登録完了: " + user.getEmail() + " (provider=" + auth.getProvider() + ")");

        return new RegisterResult(true, user.getId(), "登録が完了しました");
    }

    // ---- バリデーション ----

    private void validatePasswordInput(RegisterInput input) {
        if (input.getEmail() == null || !input.getEmail().contains("@")) {
            throw new IllegalArgumentException("メールアドレスが無効です");
        }
        if (input.getPassword() == null || input.getPassword().length() < 8) {
            throw new IllegalArgumentException("パスワードは8文字以上必要です");
        }
        if (input.getName() == null || input.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("名前は必須です");
        }
    }

    private void validateOAuthInfo(OAuthProvider.OAuthUserInfo info) {
        if (info.getEmail() == null || !info.getEmail().contains("@")) {
            throw new IllegalArgumentException("プロバイダから有効なメールアドレスを取得できませんでした");
        }
        if (info.getName() == null || info.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("プロバイダから名前を取得できませんでした");
        }
    }

    // ---- 補助処理 ----

    /** パスワードハッシュ化（簡略化）。本番では bcrypt 等のソルト付きハッシュを使う。 */
    private String hashPassword(String rawPassword) {
        return rawPassword + "_hashed";
    }

    private void sendWelcomeEmail(User user) {
        String subject = "【ハッカソン】登録完了のお知らせ";
        String body = user.getName() + " 様\n\nご登録ありがとうございます。";
        emailClient.send(user.getEmail(), subject, body);
    }

    private void linkAuthProvider(User user, String provider) {
        if (database.findAuthProvider(user.getEmail(), provider) != null) {
            throw new IllegalArgumentException("この認証プロバイダはすでに連携されています");
        }
        database.saveAuthProvider(new AuthProviderCredential(user.getEmail(), provider));
    }

    // ====================================================================
    // 動作確認用デモ
    // ====================================================================
    public static void main(String[] args) {
        UserRegistrationService service = new UserRegistrationService();

        // GitHub OAuth プロバイダを登録（client_id / secret は本来は環境変数から）
        service.registerOAuthProvider(new GitHubOAuthProvider("dummy_id", "dummy_secret"));

        // パスワード登録
        RegisterInput input = new RegisterInput();
        input.setEmail("alice@example.com");
        input.setPassword("password123");
        input.setName("Alice");
        RegisterResult r1 = service.registerWithPassword(input);
        System.out.println("password: " + r1.getMessage() + " / id=" + r1.getUserId());

        // GitHub OAuth 登録（同じ後続処理＝ウェルカムメール・ログを通る）
        RegisterResult r2 = service.registerWithOAuth("github", "mock_auth_code");
        System.out.println("github:   " + r2.getMessage() + " / id=" + r2.getUserId());
    }
}
