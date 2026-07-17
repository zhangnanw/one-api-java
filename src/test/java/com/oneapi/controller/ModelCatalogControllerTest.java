package com.oneapi.controller;

import com.oneapi.repository.ModelCatalogRepository;
import com.oneapi.entity.ModelCatalogEntry;
import com.oneapi.service.ModelCatalogService;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RequestBody;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ModelCatalogControllerTest {

    @Mock
    ModelCatalogRepository repo;

    ModelCatalogController controller;

    @BeforeEach
    void setUp() {
        controller = new ModelCatalogController(new ModelCatalogService(repo));
    }

    @Test
    void create_entryDoesNotExist_createsSuccessfully() {
        when(repo.findById("gpt-4")).thenReturn(Optional.empty());
        when(repo.save(any(ModelCatalogEntry.class))).thenAnswer(i -> i.getArgument(0));

        JsonObject body = new JsonObject()
            .put("name", "gpt-4")
            .put("capabilities", "[\"text\"]")
            .put("context_window", 8192)
            .put("input_price", 0.03)
            .put("output_price", 0.06)
            .put("reference_notes", "notes");

        controller.create(mockCtx(body));

        verify(repo).save(any(ModelCatalogEntry.class));
    }

    @Test
    void create_entryAlreadyExists_returnsBadRequest() {
        when(repo.findById("gpt-4")).thenReturn(Optional.of(new ModelCatalogEntry()));

        JsonObject body = new JsonObject().put("name", "gpt-4");
        controller.create(mockCtx(body));

        verify(repo, never()).save(any());
    }

    @Test
    void getOne_existingEntry_returnsData() {
        ModelCatalogEntry entry = new ModelCatalogEntry();
        entry.setName("gpt-4");
        entry.setCapabilities("[\"text\"]");
        entry.setContextWindow(8192);
        when(repo.findById("gpt-4")).thenReturn(Optional.of(entry));

        RoutingContext ctx = mockCtx(null);
        when(ctx.pathParam("name")).thenReturn("gpt-4");

        controller.getOne(ctx);

        assertTrue(ctx.response().ended());
    }

    private RoutingContext mockCtx(JsonObject body) {
        RoutingContext ctx = mock(RoutingContext.class);
        HttpServerResponse resp = mock(HttpServerResponse.class);
        AtomicBoolean ended = new AtomicBoolean(false);
        when(ctx.response()).thenReturn(resp);
        lenient().when(resp.setStatusCode(anyInt())).thenReturn(resp);
        lenient().when(resp.putHeader(anyString(), anyString())).thenReturn(resp);
        lenient().when(resp.ended()).thenAnswer(i -> ended.get());
        lenient().doAnswer(i -> {
            ended.set(true);
            return Future.succeededFuture();
        }).when(resp).end(anyString());
        lenient().doAnswer(i -> {
            ended.set(true);
            return Future.succeededFuture();
        }).when(resp).end();
        RequestBody rb = mock(RequestBody.class);
        if (body != null) {
            lenient().when(rb.buffer()).thenReturn(Buffer.buffer(body.toString()));
        }
        lenient().when(ctx.body()).thenReturn(rb);
        return ctx;
    }
}
