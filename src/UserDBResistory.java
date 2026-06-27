package com.youtrust.hackathon;

/**
 * User の永続化を担当するクラス。
 *
 * <p>登録サービスから User の組み立てと DB 保存を切り出すことで、
 * 保存時にどの値が User に詰められるかを単体テストしやすくする。
 */
public class UserDBResistory {

    private final UserRegistrationService.Database database;

    public UserDBResistory(UserRegistrationService.Database database) {
        this.database = database;
    }

    public UserRegistrationService.User save(UserRegistrationService.AuthenticatedUser auth) {
        UserRegistrationService.User user = new UserRegistrationService.User();
        user.setEmail(auth.getEmail());
        user.setName(auth.getName());
        user.setPassword(auth.getPasswordHash());
        database.save(user);
        return user;
    }
}
