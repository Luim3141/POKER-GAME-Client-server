package com.poker.server.session;

import com.poker.common.PasswordHasher;
import com.poker.persistence.UserAccount;
import com.poker.persistence.UserRepository;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class AuthenticationService {
    public static final long INITIAL_CHIPS = 10_000;
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{3,24}");
    private final UserRepository users;
    private final PasswordHasher passwords = new PasswordHasher();

    public AuthenticationService(UserRepository users) { this.users = users; }

    public UserAccount register(String username, char[] password) throws SQLException {
        String normalized = normalize(username);
        var hashed = passwords.hash(password);
        return users.create(normalized, hashed.hash(), hashed.salt(), hashed.iterations(), INITIAL_CHIPS);
    }

    public Optional<UserAccount> login(String username, char[] password) throws SQLException {
        var user = users.findByUsername(normalize(username));
        return user.filter(account -> account.accountStatus() == com.poker.model.AccountStatus.ACTIVE)
            .filter(account -> passwords.verify(password, account.passwordSalt(), account.passwordHash(), account.passwordIterations()));
    }

    public Optional<UserAccount> find(long id) throws SQLException { return users.findById(id); }
    public Optional<UserAccount> findByUsername(String username) throws SQLException { return users.findByUsername(normalize(username)); }

    private String normalize(String username) {
        String value = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!USERNAME.matcher(value).matches()) throw new IllegalArgumentException("Username must contain 3-24 letters, digits or underscores");
        return value;
    }
}
