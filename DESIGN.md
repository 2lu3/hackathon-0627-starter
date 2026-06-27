# 設計ドキュメント

> チーム名：（記入してください）  
> メンバー：（記入してください）

---

## 1. 課題の整理

スターターコードおよび新要件（GitHub OAuth 登録、将来の Google/LINE 拡張）に対する課題：

- `register()` に「認証方法（パスワード検証・ハッシュ化）」と「登録後処理（重複チェック・保存・メール・ログ）」が一体化している。
- このまま OAuth を足すと `if (oauth) {...} else {...}` の分岐になり、ベンダーが増えるたびにメソッドが肥大化する。
- 「パスワード登録と同じ後続処理（ウェルカムメール・ログ）を通す」という要件が、コード構造では保証されない（実装ミスで片方だけメール漏れ等が起こりうる）。
- 例外型が `Exception` で粒度が粗く、バリデーション失敗と外部プロバイダ失敗を区別できない。

---

## 2. 設計方針

**「本人確認の方法（authentication）」と「確認後の登録パイプライン（registration）」を分離する。**

```
入力(方法ごとに異なる) ──認証──▶ AuthenticatedUser(共通の中間表現) ──共通パイプライン──▶ 結果
 ・パスワード                      email / name / provider           重複チェック
 ・GitHub OAuth                   passwordHash                      DB保存
 ・(将来)Google / LINE                                              ウェルカムメール
                                                                   監査ログ
```

- 認証が成功したら、どの方法でも `AuthenticatedUser` に正規化し、共通パイプライン `register(AuthenticatedUser)` に**必ず合流**させる。これにより「同じ後続処理を通す」を構造的に保証。
- ベンダー固有処理は `OAuthProvider` インターフェースの実装に閉じ込め、新ベンダー追加＝実装クラスを1つ作って登録するだけ（開放閉鎖原則）。
- ユーザーの同一性はアプリの `email` を正とし、OAuth プロバイダ固有の外部 ID は使わない。プロバイダごとの認証情報は `email + provider` でユーザーに紐づける。
- 例外を `OAuthException`（外部起因）と `IllegalArgumentException`（バリデーション）に分離。

---

## 3. クラス・メソッド構成

```
UserRegistrationService                 // 登録のオーケストレーション
├── registerWithPassword(input)         //  入口1: パスワード → AuthenticatedUser
├── registerWithOAuth(providerId, code) //  入口2: OAuth     → AuthenticatedUser
├── register(AuthenticatedUser)         //  ★共通パイプライン（重複/保存/メール/ログ）
├── registerOAuthProvider(provider)     //  ベンダーの差し込み口（Map管理）
└── AuthenticatedUser                   //  認証成功後の正規化表現（各入口の合流点）

OAuthProvider (interface)               // ベンダー共通契約：code → OAuthUserInfo
├── GitHubOAuthProvider                 //  GitHub実装（トークン交換 + プロフィール取得）
├── (将来) GoogleOAuthProvider          //  追加するだけ。Service側は無改修
└── (将来) LineOAuthProvider

OAuthException                          // 外部プロバイダ起因の失敗を表す例外
```

---

## 4. 工夫したポイント

- **後続処理の一元化を「構造」で担保**：メール・ログを `register(AuthenticatedUser)` の1か所だけに置き、全入口がそこを通る設計にした。実装者の注意力に頼らず要件を満たす。
- **拡張点の最小化**：新ベンダー追加時の変更は「`OAuthProvider` 実装 + `registerOAuthProvider()` 呼び出し」のみ。`UserRegistrationService` と `register()` は閉じている。
- **ベンダー差異の吸収**：`OAuthUserInfo` で `email/name` に正規化し、後続がプロバイダを意識しないようにした。
- **email を正とした認証情報の紐づけ**：OAuth 側では provider 固有 ID を保存せず、プロバイダが返す認証済み email をアプリユーザーに紐づける基準にした。
- **テスト容易性**：`Database`/`EmailClient` をコンストラクタ注入にし、`OAuthProvider` もモック差し替え可能にした。

---

## 5. できなかったこと・今後の改善点

- **アカウント連携**：OAuth のメールが既存ユーザーと一致した場合、現状はエラー。次の改善では `email + provider` の認証情報を別モデルとして保存し、既存ユーザーへのプロバイダ追加連携を扱えるようにしたい。
- **OAuth セキュリティ**：`state` パラメータによる CSRF 対策はコントローラ層の責務として未実装。
- **実 HTTP 通信**：`GitHubOAuthProvider` のトークン交換・プロフィール取得はモック（差し替え箇所はコメントで明示）。GitHub のメール非公開ケース（`/user/emails`）対応も本番で必要。
- **パスワードハッシュ**：現状は簡略化（`_hashed` 連結）。本番は bcrypt 等のソルト付きハッシュに置換する。
