package com.qinzhi.promptoptimizer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qinzhi.promptoptimizer.api.OptimizeRequest;
import com.qinzhi.promptoptimizer.api.OptimizeResponse;
import com.qinzhi.promptoptimizer.api.PromptHistoryItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

@Component
public class PromptHistoryStore {
    private static final Logger LOGGER = Logger.getLogger(PromptHistoryStore.class.getName());
    private static final int MAX_ITEMS = 100;
    private final ObjectMapper objectMapper;
    private final Path historyFile;
    private final PromptQualityScorer qualityScorer;
    private final List<PromptHistoryItem> items = new ArrayList<>();

    @Autowired
    public PromptHistoryStore(
            ObjectMapper objectMapper,
            PromptQualityScorer qualityScorer,
            @Value("${app.data-dir:./data}") String dataDirectory
    ) {
        this(objectMapper, qualityScorer, Path.of(dataDirectory));
    }

    PromptHistoryStore(ObjectMapper objectMapper, PromptQualityScorer qualityScorer, Path dataDirectory) {
        this.objectMapper = objectMapper.copy().findAndRegisterModules();
        this.qualityScorer = qualityScorer;
        this.historyFile = dataDirectory.resolve("prompt-history.json");
    }

    @PostConstruct
    void initialize() {
        try {
            Path parent = historyFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            if (Files.exists(historyFile)) {
                List<PromptHistoryItem> loaded = objectMapper.readValue(
                        historyFile.toFile(),
                        new TypeReference<>() { }
                );
                if (loaded != null) {
                    items.addAll(loaded);
                }
                sortAndTrim();
            }
        } catch (IOException exception) {
            LOGGER.warning("无法读取提示词历史，将从空历史开始: " + exception.getMessage());
        }
    }

    public synchronized OptimizeResponse save(OptimizeRequest request, OptimizeResponse response) {
        String id = UUID.randomUUID().toString();
        Instant createdAt = Instant.now();
        int score = qualityScorer.score(response.optimizedPrompt());
        PromptHistoryItem item = new PromptHistoryItem(
                id,
                createdAt,
                request.originalPrompt().trim(),
                request.scene(),
                request.style(),
                response.optimizedPrompt(),
                List.copyOf(response.suggestions()),
                response.model(),
                response.mode(),
                response.degraded(),
                score,
                null
        );
        items.add(item);
        sortAndTrim();
        persist();
        return item.asResponse();
    }

    public synchronized List<PromptHistoryItem> list() {
        return List.copyOf(items);
    }

    public synchronized boolean delete(String id) {
        boolean removed = items.removeIf(item -> item.id().equals(id));
        if (removed) {
            persist();
        }
        return removed;
    }

    public synchronized PromptHistoryItem rate(String id, int rating) {
        for (int index = 0; index < items.size(); index++) {
            PromptHistoryItem item = items.get(index);
            if (item.id().equals(id)) {
                PromptHistoryItem updated = new PromptHistoryItem(
                        item.id(), item.createdAt(), item.originalPrompt(), item.scene(), item.style(),
                        item.optimizedPrompt(), item.suggestions(), item.model(), item.mode(),
                        item.degraded(), item.qualityScore(), rating
                );
                items.set(index, updated);
                persist();
                return updated;
            }
        }
        return null;
    }

    private void sortAndTrim() {
        items.sort(Comparator.comparing(PromptHistoryItem::createdAt).reversed());
        if (items.size() > MAX_ITEMS) {
            items.subList(MAX_ITEMS, items.size()).clear();
        }
    }

    private void persist() {
        try {
            Path temporary = historyFile.resolveSibling(historyFile.getFileName() + ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), items);
            try {
                Files.move(temporary, historyFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, historyFile, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("无法保存提示词历史", exception);
        }
    }
}
