package com.example.wallet.domain.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.wallet.common.exception.InsufficientFundsException;
import com.example.wallet.common.money.Funds;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class InMemoryWalletTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Currency EUR = Currency.getInstance("EUR");

    private final Wallet wallet = new InMemoryWallet();

    @Test
    void pdfWorkflowBalances() {
        wallet.deposit(usd("10"));
        wallet.deposit(new Funds(EUR, new BigDecimal("5")));
        wallet.withdraw(usd("3"));

        assertThat(wallet.balances().get(USD)).isEqualByComparingTo("7");
        assertThat(wallet.balances().get(EUR)).isEqualByComparingTo("5");
    }

    @Test
    void withdrawingMoreThanTheBalanceFailsAndLeavesItUnchanged() {
        wallet.deposit(usd("7"));

        assertThatThrownBy(() -> wallet.withdraw(usd("8")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Cannot withdraw 8.00 USD, available balance is 7.00 USD");
        assertThat(wallet.balances().get(USD)).isEqualByComparingTo("7");
    }

    @Test
    void withdrawingACurrencyNeverDepositedFails() {
        assertThatThrownBy(() -> wallet.withdraw(usd("1")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Cannot withdraw 1.00 USD, available balance is 0.00 USD");
    }

    @Test
    void withdrawingTheWholeBalanceKeepsTheCurrencyAtZero() {
        wallet.deposit(usd("7.50"));
        wallet.withdraw(usd("7.5"));

        assertThat(wallet.balances()).containsOnlyKeys(USD);
        assertThat(wallet.balances().get(USD)).isEqualByComparingTo("0");
    }

    @Test
    void balancesIsASnapshot() {
        wallet.deposit(usd("1"));
        var snapshot = wallet.balances();
        wallet.deposit(usd("1"));

        assertThat(snapshot.get(USD)).isEqualByComparingTo("1");
    }

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        wallet.deposit(usd("50"));
        var successes = new AtomicInteger();
        var rejections = new AtomicInteger();

        runConcurrently(
                100,
                () -> {
                    try {
                        wallet.withdraw(usd("1"));
                        successes.incrementAndGet();
                    } catch (InsufficientFundsException e) {
                        rejections.incrementAndGet();
                    }
                });

        assertThat(successes).hasValue(50);
        assertThat(rejections).hasValue(50);
        assertThat(wallet.balances().get(USD)).isEqualByComparingTo("0");
    }

    @Test
    void concurrentDepositsAreNotLost() throws Exception {
        runConcurrently(1000, () -> wallet.deposit(usd("0.01")));

        assertThat(wallet.balances().get(USD)).isEqualByComparingTo("10.00");
    }

    /**
     * Starts all tasks at the same moment (latch) to maximise contention, then waits and rethrows
     * any failure.
     */
    private static void runConcurrently(int tasks, Runnable task) throws Exception {
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<?>>();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < tasks; i++) {
                futures.add(
                        executor.submit(() -> {
                            start.await();
                            task.run();
                            return null;
                        }));
            }
            start.countDown();
        }
        for (Future<?> future : futures) {
            future.get();
        }
    }

    private static Funds usd(String amount) {
        return new Funds(USD, new BigDecimal(amount));
    }
}
