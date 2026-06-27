package com.youtrust.hackathon;

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
    private final EmailClient emailClient;

    /** providerId -> OAuthProvider。登録済みのベンダーを保持する。 */
    private final Map<String, OAuthProvider> oauthProviders = new HashMap<>();

    public UserRegistrationService() {
        this(new Database(), new EmailClient());
    }

    /** 依存を注入するコンストラクタ（テスト時にモックを差し込めるようにする）。 */
    public UserRegistrationService(Database database, EmailClient emailClient) {
        this.database = database;
        this.emailClient = emailClient;
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
                null,                          // 外部 ID なし
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
                info.getExternalId(),
                null);                         // パスワードなし

        return register(auth);
    }

    // ====================================================================
    // 共通登録パイプライン: すべての入口が必ずここを通る
    // ====================================================================
    private RegisterResult register(AuthenticatedUser auth) {
        // 重複チェック
        if (database.findByEmail(auth.getEmail()) != null) {
            throw new IllegalArgumentException("このメールアドレスはすでに登録されています");
        }

        // DB 保存
        User user = new User();
        user.setEmail(auth.getEmail());
        user.setName(auth.getName());
        user.setPassword(auth.getPasswordHash());   // OAuth の場合は null
        user.setProvider(auth.getProvider());
        user.setExternalId(auth.getExternalId());
        database.save(user);

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

    // ====================================================================
    // 認証成功後の正規化された本人情報（各入口の合流点）
    // ====================================================================
    static class AuthenticatedUser {
        private final String email;
        private final String name;
        private final String provider;     // "password" / "github" / ...
        private final String externalId;   // OAuth のみ。プロバイダ側の一意 ID
        private final String passwordHash; // パスワード登録のみ

        AuthenticatedUser(String email, String name, String provider,
                          String externalId, String passwordHash) {
            this.email = email;
            this.name = name;
            this.provider = provider;
            this.externalId = externalId;
            this.passwordHash = passwordHash;
        }

        public String getEmail() { return email; }
        public String getName() { return name; }
        public String getProvider() { return provider; }
        public String getExternalId() { return externalId; }
        public String getPasswordHash() { return passwordHash; }
    }

    // ---- 以下はモッククラス ----

    static class Database {
        public User findByEmail(String email) { return null; }
        public void save(User user) { user.setId("user_" + System.currentTimeMillis()); }
    }

    static class EmailClient {
        public void send(String to, String subject, String body) {
            System.out.println("Email sent to: " + to);
        }
    }

    static class User {
        private String id;
        private String email;
        private String name;
        private String password;   // OAuth 登録では null
        private String provider;   // 登録経路（"password" / "github" / ...）
        private String externalId; // OAuth プロバイダ側の一意 ID

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
        public String getProvider() { return provider; }
        public void setProvider(String provider) { this.provider = provider; }
        public String getExternalId() { return externalId; }
        public void setExternalId(String externalId) { this.externalId = externalId; }
    }

    static class RegisterInput {
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

    static class RegisterResult {
        private final boolean success;
        private final String userId;
        private final String message;
        public RegisterResult(boolean success, String userId, String message) {
            this.success = success;
            this.userId = userId;
            this.message = message;
        }
        public boolean isSuccess() { return success; }
        public String getUserId() { return userId; }
        public String getMessage() { return message; }
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
