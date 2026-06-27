package com.youtrust.hackathon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link GitHubOAuthProvider} の単体テスト。
 *
 * <p>HTTP 通信はモック実装になっているため、ここでは
 * 「認可コードのバリデーション」「クライアント設定の検証」「正規化結果」という
 * プロバイダ実装が責務として持つ振る舞いを検証する。
 */
@DisplayName("GitHubOAuthProvider")
class GitHubOAuthProviderTest {

    private final GitHubOAuthProvider provider =
            new GitHubOAuthProvider("client_id", "client_secret");

    @Test
    @DisplayName("プロバイダ ID は github")
    void exposesProviderId() {
        assertThat(provider.providerId()).isEqualTo("github");
    }

    @Test
    @DisplayName("正常な認可コードからユーザー情報を正規化して返す")
    void fetchesUserInfoForValidCode() throws OAuthException {
        OAuthProvider.OAuthUserInfo info = provider.fetchUserInfo("valid_code");

        assertThat(info.getEmail()).isEqualTo("octocat@example.com");
        assertThat(info.getName()).isEqualTo("The Octocat");
    }

    @Test
    @DisplayName("null の認可コードは OAuthException を投げる")
    void rejectsNullCode() {
        assertThatThrownBy(() -> provider.fetchUserInfo(null))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("認可コードが空です");
    }

    @Test
    @DisplayName("空白だけの認可コードは OAuthException を投げる")
    void rejectsBlankCode() {
        assertThatThrownBy(() -> provider.fetchUserInfo("   "))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("認可コードが空です");
    }

    @Test
    @DisplayName("クライアント設定が未構成だと OAuthException を投げる")
    void rejectsMissingClientConfig() {
        GitHubOAuthProvider unconfigured = new GitHubOAuthProvider(null, null);

        assertThatThrownBy(() -> unconfigured.fetchUserInfo("valid_code"))
                .isInstanceOf(OAuthException.class)
                .hasMessageContaining("クライアント設定が未構成");
    }

    @Test
    @DisplayName("設定が揃っていれば例外を投げない")
    void doesNotThrowWhenConfigured() {
        assertDoesNotThrow(() -> provider.fetchUserInfo("valid_code"));
    }
}
