package com.poker.persistence;

import com.poker.model.AccountRole;
import com.poker.model.AccountStatus;

public record UserAccount(long id, String username, byte[] passwordHash, byte[] passwordSalt,
                          int passwordIterations, long chips, long wins, long gamesPlayed,
                          AccountRole accountRole, AccountStatus accountStatus) {}
