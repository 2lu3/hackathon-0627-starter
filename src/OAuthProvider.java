package com.youtrust.hackathon;

/**
 * 外部 ID プロバイダ（GitHub / 将来の Google・LINE など）による認証の共通インターフェース。
 *
 * <p>「認可コード → ユーザー情報」という流れだけを共通の契約として定義し、
 * ベンダー固有の処理（トークンエンドポイントの URL、プロフィール JSON のマッピング等）は
 * 各実装クラスに閉じ込める。
 *
 * <p>新しいベンダーを追加するときは、このインターフェースを実装したクラスを 1 つ作り、
 * {@link UserRegistrationService#registerOAuthProvider(OAuthProvider)} で登録するだけでよい。
 * {@code UserRegistrationService} 側の登録パイプラインは一切変更しなくて済む（開放閉鎖原則）。
 */
public interface OAuthProvider {

    /**
     * プロバイダ識別子（例: "github", "google", "line"）。
     * 登録・振り分けのキーになるため、プロバイダ間で一意であること。
     */
    String providerId();

    /**
     * 認可コードを使ってアクセストークンを取得し、ユーザープロフィールを返す。
     *
     * @param authorizationCode OAuth コールバックで受け取る認可コード
     * @return 全ベンダー共通形式に正規化したユーザー情報
     * @throws OAuthException 認可コードが不正、トークン交換失敗、プロフィール取得失敗などのとき
     */
    OAuthUserInfo fetchUserInfo(String authorizationCode) throws OAuthException;

    /**
     * プロバイダから取得したユーザー情報を、ベンダー差異を吸収した共通表現にしたもの。
     *
     * <p>これを介すことで、後続の登録パイプライン（重複チェック・保存・ウェルカムメール・ログ）は
     * どのプロバイダ経由かを意識せずに処理できる。
     */
    final class OAuthUserInfo {

        /** プロバイダ側で一意なユーザー ID（例: GitHub の数値 id）。アカウント連携の基準に使える。 */
        private final String externalId;
        private final String email;
        private final String name;

        public OAuthUserInfo(String externalId, String email, String name) {
            this.externalId = externalId;
            this.email = email;
            this.name = name;
        }

        public String getExternalId() {
            return externalId;
        }

        public String getEmail() {
            return email;
        }

        public String getName() {
            return name;
        }
    }
}
