package com.qinzhi.promptoptimizer.api;

import com.qinzhi.promptoptimizer.service.PromptHistoryStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@RestController
@RequestMapping("/api/prompts/history")
public class PromptHistoryController {
    private final PromptHistoryStore historyStore;

    public PromptHistoryController(PromptHistoryStore historyStore) {
        this.historyStore = historyStore;
    }

    @GetMapping
    public List<PromptHistoryItem> list() {
        return historyStore.list();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        return historyStore.delete(id)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @PutMapping("/{id}/feedback")
    public ResponseEntity<PromptHistoryItem> rate(
            @PathVariable String id,
            @Valid @RequestBody PromptFeedbackRequest request
    ) {
        PromptHistoryItem updated = historyStore.rate(id, request.rating());
        return updated == null
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(updated);
    }
}
