package com.maimai.support.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/support")
public class TinaChatController {
    private final TinaChatService chat;
    public TinaChatController(TinaChatService chat) { this.chat=chat; }
    public record Send(@NotBlank @Pattern(regexp="[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}") String requestId,
                       @NotBlank @Size(max=1000) String message) { }
    @GetMapping("/assistant") public ResponseEntity<TinaChatService.Assistant> assistant() { return ResponseEntity.ok().header("Cache-Control","no-store").body(chat.assistant()); }
    @GetMapping("/chat") public ResponseEntity<List<TinaChatStore.Turn>> history() { return ResponseEntity.ok().header("Cache-Control","no-store").body(chat.history()); }
    @PostMapping("/chat") public ResponseEntity<TinaChatStore.Turn> send(@Valid @RequestBody Send input) { return ResponseEntity.ok().header("Cache-Control","no-store").body(chat.send(input.requestId(),input.message())); }
    @DeleteMapping("/chat") public ResponseEntity<Void> clear() { chat.clear();return ResponseEntity.noContent().build(); }
}
