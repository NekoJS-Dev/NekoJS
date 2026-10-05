//? if neoforge && >=26 {
package com.tkisor.nekojs.client.ui;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ticket42HostTransactionBoundaryTest {
    @Test
    void escapedTransactionRejectsEveryWorkerOperationBeforeChangingTheVisibleFrame() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            fixture.commitLabel(Map.of("id", "label", "text", "Active"), 60, 9);
            var before = fixture.adapter.inspect();
            var painted = fixture.paint();
            List<Consumer<JsxHostAdapter.JsxHostTransaction>> operations = operations();
            try (var worker = Executors.newSingleThreadExecutor()) {
                for (var operation : operations) {
                    var transaction = fixture.adapter.begin();
                    Throwable failure = worker.submit(() -> {
                        try {
                            operation.accept(transaction);
                            return null;
                        } catch (Throwable caught) {
                            return caught;
                        }
                    }).get();
                    assertTrue(failure instanceof IllegalStateException, "Worker transaction must be rejected before mutation: " + failure);
                    assertTrue(failure.getMessage().contains("NEKO-7004"), failure.getMessage());
                    assertEquals(before, fixture.adapter.inspect());
                    assertEquals(painted, fixture.paint());
                    transaction.rollback();
                }
            }
        }
    }

    @Test
    void savedTransactionsCannotMutateAfterTheirGenerationCloses() throws Exception {
        try (NativeUiScreenFixture fixture = new NativeUiScreenFixture()) {
            List<JsxHostAdapter.JsxHostTransaction> transactions = operations().stream()
                    .map(operation -> fixture.adapter.begin()).toList();
            fixture.globals.close();
            List<Consumer<JsxHostAdapter.JsxHostTransaction>> operations = operations();
            for (int index = 0; index < operations.size(); index++) {
                var transaction = transactions.get(index);
                var operation = operations.get(index);
                IllegalStateException failure = assertThrows(IllegalStateException.class, () -> operation.accept(transaction));
                assertTrue(failure.getMessage().contains("NEKO-7001"), failure.getMessage());
                assertTrue(fixture.paint().isEmpty());
            }
        }
    }

    private static List<Consumer<JsxHostAdapter.JsxHostTransaction>> operations() {
        return List.of(transaction -> transaction.create("label", "candidate", Map.of("text", "Candidate")),
                transaction -> transaction.update(null, "label", "candidate", Map.of()),
                transaction -> transaction.order(null, List.of()), transaction -> transaction.remove(null),
                transaction -> transaction.commit(List.of()), JsxHostAdapter.JsxHostTransaction::rollback);
    }
}
//?}
