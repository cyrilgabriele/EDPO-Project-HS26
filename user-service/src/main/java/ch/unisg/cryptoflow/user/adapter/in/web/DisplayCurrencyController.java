package ch.unisg.cryptoflow.user.adapter.in.web;

import ch.unisg.cryptoflow.user.application.port.in.UpdateDisplayCurrencyCommand;
import ch.unisg.cryptoflow.user.application.port.in.UpdateDisplayCurrencyUseCase;
import ch.unisg.cryptoflow.user.application.service.UpdateDisplayCurrencyService;
import ch.unisg.cryptoflow.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class DisplayCurrencyController {

    private final UpdateDisplayCurrencyUseCase updateDisplayCurrencyUseCase;

    @PatchMapping("/{userId}/display-currency")
    public ResponseEntity<Map<String, String>> updateDisplayCurrency(
            @PathVariable String userId,
            @RequestBody DisplayCurrencyRequest body) {
        Objects.requireNonNull(body, "request body required");
        User updated = updateDisplayCurrencyUseCase.updateDisplayCurrency(
                new UpdateDisplayCurrencyCommand(userId, body.displayCurrency()));
        return ResponseEntity.ok(Map.of(
                "userId", updated.userId(),
                "displayCurrency", updated.displayCurrency()));
    }

    public record DisplayCurrencyRequest(String displayCurrency) {}

    @ExceptionHandler(UpdateDisplayCurrencyService.UserNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(UpdateDisplayCurrencyService.UserNotFoundException ex) {
        return ResponseEntity.status(404).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(UpdateDisplayCurrencyService.UnsupportedDisplayCurrencyException.class)
    public ResponseEntity<Map<String, String>> badCurrency(UpdateDisplayCurrencyService.UnsupportedDisplayCurrencyException ex) {
        return ResponseEntity.status(400).body(Map.of("error", ex.getMessage()));
    }
}
