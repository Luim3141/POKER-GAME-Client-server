package com.poker.server.table;

import com.poker.game.Street;

/** Public accepted action, never private cards or client-supplied identity. */
public record TableAction(long sequence, long userId, Street street, String action,
                          long paid, long streetContribution, boolean automatic) { }
