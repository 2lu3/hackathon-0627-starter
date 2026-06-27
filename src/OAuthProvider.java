package com.youtrust.hackathon;

/**
 * OAuth プロバイダ（GitHub / 将来の Google・LINE など）による認証の共通インターフェース。
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
     * <p>このコードベースではアプリのユーザー同一性を email に寄せるため、
     * プロバイダ固有 ID は使わず、認証済み email を後続処理の基準にする。
     */
    final class OAuthUserInfo {

        private final String email;
        private final String name;

        public OAuthUserInfo(String email, String name) {
            this.email = email;
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public String getName() {
            return name;
        }
    }
}
