# 設計判断ログ

> チーム名：（記入してください）

---

## 判断ログの書き方

設計中に「なぜこうしたか」という判断を都度記録してください。
小さな判断でもOKです。審査員はこのログを重視します。

---

## 判断ログ一覧

### [ADR-001] Github Oauth

**日時**：

16:57

**状況**：

* GithubOauthと既存のコードをmergeする方法
  * 詳しく設計をする
  * AIにとりあえず書いてもらってそのあと考える

**決定**：

* AIにとりあえず書いてもらってそのあと考える

**理由**：

* 共通認識をとるのに、コードがないと時間がかかるから

**トレードオフ**：

* 詳しく設計する
  * 正確だが、時間がかかる

---

### [ADR-002] Githubのブランチ運用

**日時**：

17:07

**状況**：
GitHub OAuth 登録を追加するにあたり、(a) 既存 `register()` に分岐を足す案、(b) 認証方法と登録後処理を分離する案があった。要件で「パスワード登録と同じ後続処理（メール・ログ）を通す」ことが求められていた。

* githubのbranchをmainだけにするか分けるか

**決定**：
認証方法ごとに入口（`registerWithPassword` / `registerWithOAuth`）を分け、認証結果を `AuthenticatedUser` に正規化して共通パイプライン `register(AuthenticatedUser)` に合流させた。メール・ログはこの1か所だけに置いた。

* 分ける

**理由**：
「同じ後続処理を通す」という要件を、実装者の注意ではなく構造で保証できる。入口が増えても合流点は1つなので後続処理の重複・漏れが起きない。

* conflictとかを気にしなくていい

**トレードオフ**：
分岐案（a）はファイル数が少なく短期的には簡単だが、ベンダー追加のたびに `register()` が肥大化し、後続処理の二重管理リスクが残るため不採用。

* ブランチごとにコミットが大量にできるとmergeが大変

---

### [ADR-003] 1ユーザー1メールアドレスでいい？

**日時**：2026-06-27

17:10

**状況**：
将来 Google / LINE での登録も追加する想定。各社で「トークン交換 URL」「プロフィール JSON の形」が異なる。

* 1ユーザー1メールアドレスとしていいか
* Oauth providerではemailアドレスをくれないところもある

**決定**：
`OAuthProvider`（`providerId()` と `fetchUserInfo(code)`）を定義し、`GitHubOAuthProvider` で実装。`UserRegistrationService` は `providerId -> OAuthProvider` の Map を持ち、`registerOAuthProvider()` で差し込む。

* して良い

**理由**：
新ベンダー追加が「インターフェース実装1クラス + 登録1行」で済み、`UserRegistrationService` を変更しなくてよい（開放閉鎖原則）。`OAuthUserInfo` で差異を吸収するため後続処理はプロバイダ非依存。

* Googleとgithubはできるから
* 時間がかかる
* DBの設計が煩雑になるが、それは今回のハッカソンの本質ではないため

**トレードオフ**：

* appleでログインを実装するなら、コアロジックを変更する必要がある


---

### [ADR-004] DatabaseRepositoryを作成する

**日時**：2026-06-27

17:23

**状況**：
元コードは `throws Exception` で、バリデーション失敗と外部プロバイダ起因の失敗が区別できなかった。

* UserRegistrationServiceの中で、userをDBに登録する処理が書かれている

**決定**：
バリデーションは `IllegalArgumentException`、OAuth の外部起因失敗は専用の `OAuthException` を新設。サービス境界では `OAuthException` を `IllegalStateException` に変換して呼び出し側に渡す。

user repositoryとして、crudを別のクラスに切り出す

**理由**：
呼び出し側が「ユーザー入力の問題」か「外部サービスの問題」かをハンドリングで判別でき、リトライ要否などの判断ができる。

user create / update / delete とかを別に作ると、単体テストができ、クリーンアーキテクチャー的になる

**トレードオフ**：
例外クラスが増えるが、粒度の粗い `Exception` のままにする方が後の保守コストが高いと判断した。

コード量が増える

---

### [ADR-005] Domainモデルがserviceに入っている

**日時**：

17:32

**状況**：

domainモデルがserviceに入っているのがキモい

**決定**：

他のファイルに分ける

**理由**：

循環参照になってる

**トレードオフ**：

コードの量が増える

---

### [ADR-005] OAuth の同一性判定は externalId ではなく email を正とする

**日時**：2026-06-27

**状況**：
OAuth プロバイダごとのユーザー ID（GitHub の id、Google の sub、LINE の userId）を使う案と、アプリのユーザー email を正として認証情報を紐づける案があった。

**決定**：
本コードベースでは externalId を使わず、OAuth プロバイダから取得した email をユーザー同一性の基準にする。provider ごとの認証情報は `email + provider` でユーザーに紐づける。

**理由**：
既存の登録処理が email を重複チェックの基準にしており、パスワード登録と OAuth 登録を同じユーザーモデルに合流させやすい。今回の要件ではプロバイダ固有 ID よりも「YOUTRUST 側のユーザー email を正とする」方針を優先する。

**トレードオフ**：
GitHub 側で email が非公開または変更された場合の扱いは弱くなる。そのため本番実装では、プロバイダから取得する email が verified であることの確認、email が取得できない場合の追加入力フロー、既存 email への provider 追加連携フローを別途設計する必要がある。

---

### [ADR-006] User の DB 保存を UserDBResistory に切り出した

**日時**：2026-06-27

**状況**：
`UserRegistrationService` の共通登録パイプライン内で、`User` の生成、値の詰め替え、`Database.save()` 呼び出しを直接行っていた。このままだと保存時にどの値が永続化対象になるかを単体テストしづらい。

**決定**：
`UserDBResistory` を追加し、`AuthenticatedUser` から `User` を組み立てて DB 保存する責務を移した。`UserRegistrationService` には `UserDBResistory` をコンストラクタ注入できるようにした。

**理由**：
登録フロー全体を動かさなくても、保存処理だけを fake database で検証できる。サービス本体は登録フローの制御に集中し、保存時のデータ変換は別クラスで扱える。

**トレードオフ**：
小規模なコードではクラスが1つ増えるが、保存処理の責務が明確になり、テストの粒度を小さくできるため採用した。

---

### [ADR-007] domain model を UserRegistrationService の inner class から分離した

**日時**：2026-06-27

**状況**：
`User`、`AuthenticatedUser`、`AuthProviderCredential`、`RegisterInput`、`RegisterResult` が `UserRegistrationService` の inner class として定義されていた。この状態だと domain model が service 層に従属して見え、`UserDBResistory` やテストからも `UserRegistrationService.User` のように参照する必要があった。

**決定**：
各 model / DTO を `src` 配下の独立 class に切り出した。`UserRegistrationService` はそれらを利用するだけにし、domain model を所有しない構造にした。

**理由**：
domain model を service から独立させることで、保存処理・認証処理・テストから自然に再利用できる。サービスの責務は登録フローの制御に限定され、モデルの所属も明確になる。

**トレードオフ**：
ファイル数は増えるが、モデルの参照関係が `UserRegistrationService.X` から通常の class 参照になり、責務境界が読み取りやすくなるため採用した。

---

<!-- 判断が増えたらADR-005, ADR-006 ... と追加してください -->
