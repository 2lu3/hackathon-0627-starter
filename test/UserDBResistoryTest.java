package com.youtrust.hackathon;

public class UserDBResistoryTest {

    public static void main(String[] args) {
        savesAuthenticatedUserAsUser();
        System.out.println("UserDBResistoryTest passed");
    }

    private static void savesAuthenticatedUserAsUser() {
        RecordingDatabase database = new RecordingDatabase();
        UserDBResistory resistory = new UserDBResistory(database);

        AuthenticatedUser auth =
                new AuthenticatedUser(
                        "alice@example.com",
                        "Alice",
                        "password",
                        "password123_hashed");

        User saved = resistory.save(auth);

        assertEquals("user_test", saved.getId(), "id");
        assertEquals("alice@example.com", database.savedUser.getEmail(), "email");
        assertEquals("Alice", database.savedUser.getName(), "name");
        assertEquals("password123_hashed", database.savedUser.getPassword(), "password");
    }

    private static void assertEquals(String expected, String actual, String label) {
        if (!expected.equals(actual)) {
            throw new AssertionError(label + " expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static class RecordingDatabase extends UserRegistrationService.Database {
        private User savedUser;

        @Override
        public void save(User user) {
            this.savedUser = user;
            user.setId("user_test");
        }
    }
}
