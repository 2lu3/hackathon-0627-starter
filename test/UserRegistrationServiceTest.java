package com.youtrust.hackathon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserRegistrationService} の単体テスト。
 *
 * <p>外部依存（DB / メール送信 / OAuth プロバイダ）はすべて Mockito でモック化し、
 * 「どの認証入口でも共通の登録パイプラインを必ず通る」というこのクラスの設計意図を、
 * 振る舞い（保存・メール送信・プロバイダ連携・例外）の観点から検証する。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserRegistrationService")
class UserRegistrationServiceTest {

    @Mock
    private UserRegistrationService.Database database;

    @Mock
    private UserRegistrationService.EmailClient emailClient;

    @Mock
    private UserDBResistory userDBResistory;

    private UserRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new UserRegistrationService(database, emailClient, userDBResistory);
    }

    /** save 時に id を採番した User を返す共通スタブ。 */
    private User stubSavedUser(String email, String name) {
        User user = new User();
        user.setId("user_1");
        user.setEmail(email);
        user.setName(name);
        when(userDBResistory.save(any(AuthenticatedUser.class))).thenReturn(user);
        return user;
    }

    // ================================================================
    @Nested
    @DisplayName("registerWithPassword")
    class RegisterWithPassword {

        private RegisterInput validInput() {
            RegisterInput input = new RegisterInput();
            input.setEmail("alice@example.com");
            input.setPassword("password123");
            input.setName("Alice");
            return input;
        }

        @Test
        @DisplayName("新規ユーザーを保存し、ウェルカムメールを送り、成功結果を返す")
        void registersNewUser() {
            when(database.findByEmail("alice@example.com")).thenReturn(null);
            User saved = stubSavedUser("alice@example.com", "Alice");

            RegisterResult result = service.registerWithPassword(validInput());

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getUserId()).isEqualTo("user_1");
            assertThat(result.getMessage()).isEqualTo("登録が完了しました");

            verify(emailClient).send(eq("alice@example.com"), anyString(), anyString());
            // パスワード登録では認証プロバイダ連携を行わない
            verify(database, never()).saveAuthProvider(any(AuthProviderCredential.class));
            assertThat(saved.getId()).isEqualTo("user_1");
        }

        @Test
        @DisplayName("生パスワードではなくハッシュ化した値を永続化層に渡す")
        void passesHashedPasswordToRepository() {
            when(database.findByEmail(anyString())).thenReturn(null);
            stubSavedUser("alice@example.com", "Alice");

            service.registerWithPassword(validInput());

            ArgumentCaptor<AuthenticatedUser> captor = ArgumentCaptor.forClass(AuthenticatedUser.class);
            verify(userDBResistory).save(captor.capture());
            AuthenticatedUser auth = captor.getValue();
            assertThat(auth.getProvider()).isEqualTo("password");
            assertThat(auth.getPasswordHash())
                    .isNotEqualTo("password123")
                    .contains("password123");
        }

        @Test
        @DisplayName("メールアドレスが既に存在する場合は例外を投げ、保存もメール送信もしない")
        void rejectsDuplicateEmail() {
            User existing = new User();
            existing.setId("user_existing");
            existing.setEmail("alice@example.com");
            when(database.findByEmail("alice@example.com")).thenReturn(existing);

            assertThatThrownBy(() -> service.registerWithPassword(validInput()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("すでに登録されています");

            verify(userDBResistory, never()).save(any());
            verify(emailClient, never()).send(anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("@ を含まないメールアドレスは拒否する")
        void rejectsInvalidEmail() {
            RegisterInput input = validInput();
            input.setEmail("invalid-email");

            assertThatThrownBy(() -> service.registerWithPassword(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("メールアドレスが無効です");

            verify(userDBResistory, never()).save(any());
        }

        @Test
        @DisplayName("null のメールアドレスは拒否する")
        void rejectsNullEmail() {
            RegisterInput input = validInput();
            input.setEmail(null);

            assertThatThrownBy(() -> service.registerWithPassword(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("メールアドレスが無効です");
        }

        @Test
        @DisplayName("8文字未満のパスワードは拒否する")
        void rejectsTooShortPassword() {
            RegisterInput input = validInput();
            input.setPassword("short");

            assertThatThrownBy(() -> service.registerWithPassword(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("パスワードは8文字以上");
        }

        @Test
        @DisplayName("空白だけの名前は拒否する")
        void rejectsBlankName() {
            RegisterInput input = validInput();
            input.setName("   ");

            assertThatThrownBy(() -> service.registerWithPassword(input))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("名前は必須です");
        }
    }

    // ================================================================
    @Nested
    @DisplayName("registerWithOAuth")
    class RegisterWithOAuth {

        @Mock
        private OAuthProvider githubProvider;

        @BeforeEach
        void registerProvider() {
            // providerId() は登録時に必ず呼ばれる
            when(githubProvider.providerId()).thenReturn("github");
            service.registerOAuthProvider(githubProvider);
        }

        @Test
        @DisplayName("未登録の新規ユーザーを保存し、認証プロバイダを連携し、ウェルカムメールを送る")
        void registersNewOAuthUser() throws Exception {
            when(githubProvider.fetchUserInfo("auth_code"))
                    .thenReturn(new OAuthProvider.OAuthUserInfo("octocat@example.com", "The Octocat"));
            when(database.findByEmail("octocat@example.com")).thenReturn(null);
            when(database.findAuthProvider("octocat@example.com", "github")).thenReturn(null);
            stubSavedUser("octocat@example.com", "The Octocat");

            RegisterResult result = service.registerWithOAuth("github", "auth_code");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getMessage()).isEqualTo("登録が完了しました");

            verify(emailClient).send(eq("octocat@example.com"), anyString(), anyString());
            // OAuth 登録では認証プロバイダ連携を行う
            ArgumentCaptor<AuthProviderCredential> captor =
                    ArgumentCaptor.forClass(AuthProviderCredential.class);
            verify(database).saveAuthProvider(captor.capture());
            assertThat(captor.getValue().getEmail()).isEqualTo("octocat@example.com");
            assertThat(captor.getValue().getProvider()).isEqualTo("github");
        }

        @Test
        @DisplayName("既存ユーザーには新規保存せず、認証プロバイダの連携だけを行う")
        void linksProviderToExistingUser() throws Exception {
            User existing = new User();
            existing.setId("user_existing");
            existing.setEmail("octocat@example.com");
            when(githubProvider.fetchUserInfo("auth_code"))
                    .thenReturn(new OAuthProvider.OAuthUserInfo("octocat@example.com", "The Octocat"));
            when(database.findByEmail("octocat@example.com")).thenReturn(existing);
            when(database.findAuthProvider("octocat@example.com", "github")).thenReturn(null);

            RegisterResult result = service.registerWithOAuth("github", "auth_code");

            assertThat(result.isSuccess()).isTrue();
            assertThat(result.getUserId()).isEqualTo("user_existing");
            assertThat(result.getMessage()).isEqualTo("認証プロバイダを連携しました");

            verify(userDBResistory, never()).save(any());
            verify(emailClient, never()).send(anyString(), anyString(), anyString());
            verify(database).saveAuthProvider(any(AuthProviderCredential.class));
        }

        @Test
        @DisplayName("未対応のプロバイダ ID は IllegalArgumentException を投げる")
        void rejectsUnknownProvider() {
            assertThatThrownBy(() -> service.registerWithOAuth("line", "auth_code"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("未対応の認証プロバイダ");
        }

        @Test
        @DisplayName("プロバイダの OAuthException は IllegalStateException に変換して再送出する")
        void wrapsOAuthExceptionAsIllegalState() throws Exception {
            when(githubProvider.fetchUserInfo("bad_code"))
                    .thenThrow(new OAuthException("認可コードが空です"));

            assertThatThrownBy(() -> service.registerWithOAuth("github", "bad_code"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("OAuth 認証に失敗しました")
                    .hasCauseInstanceOf(OAuthException.class);

            verify(userDBResistory, never()).save(any());
        }

        @Test
        @DisplayName("プロバイダが有効なメールを返さない場合は拒否する")
        void rejectsMissingEmailFromProvider() throws Exception {
            when(githubProvider.fetchUserInfo("auth_code"))
                    .thenReturn(new OAuthProvider.OAuthUserInfo(null, "The Octocat"));

            assertThatThrownBy(() -> service.registerWithOAuth("github", "auth_code"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("有効なメールアドレスを取得できませんでした");

            verify(userDBResistory, never()).save(any());
        }

        @Test
        @DisplayName("プロバイダが名前を返さない場合は拒否する")
        void rejectsMissingNameFromProvider() throws Exception {
            when(githubProvider.fetchUserInfo("auth_code"))
                    .thenReturn(new OAuthProvider.OAuthUserInfo("octocat@example.com", "  "));

            assertThatThrownBy(() -> service.registerWithOAuth("github", "auth_code"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("名前を取得できませんでした");
        }

        @Test
        @DisplayName("既に同じ認証プロバイダが連携済みなら拒否する")
        void rejectsAlreadyLinkedProvider() throws Exception {
            User existing = new User();
            existing.setId("user_existing");
            existing.setEmail("octocat@example.com");
            when(githubProvider.fetchUserInfo("auth_code"))
                    .thenReturn(new OAuthProvider.OAuthUserInfo("octocat@example.com", "The Octocat"));
            when(database.findByEmail("octocat@example.com")).thenReturn(existing);
            when(database.findAuthProvider("octocat@example.com", "github"))
                    .thenReturn(new AuthProviderCredential("octocat@example.com", "github"));

            assertThatThrownBy(() -> service.registerWithOAuth("github", "auth_code"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("すでに連携されています");

            verify(database, never()).saveAuthProvider(any());
        }
    }
}
