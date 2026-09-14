package com.maimai.messaging.controller;

import com.maimai.common.security.SecurityUtils;
import com.maimai.messaging.dto.MessageDtos.*;
import com.maimai.messaging.service.MessageService;
import com.maimai.messaging.service.MessageImageService;
import jakarta.validation.Valid;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {
    private final MessageService messages;
    private final MessageImageService images;
    public MessageController(MessageService messages,MessageImageService images) {this.messages=messages;this.images=images;}
    @GetMapping("/users/{id}/block")
    public BlockState blockState(@PathVariable long id) {
        return messages.blockState(SecurityUtils.currentUserId(),id);
    }
    @PutMapping("/users/{id}/block")
    public BlockState setBlock(@PathVariable long id,@RequestBody @Valid SetBlock request) {
        return messages.setBlock(SecurityUtils.currentUserId(),id,request.blocked());
    }
    @PostMapping("/conversations")
    public Map<String,Long> open(@RequestBody @Valid OpenConversation request) {
        return Map.of("id",messages.open(SecurityUtils.currentUserId(),request));
    }
    @GetMapping("/conversations")
    public List<Conversation> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
                                   @RequestParam(defaultValue="") String keyword,@RequestParam(defaultValue="false") boolean unreadOnly) {
        return messages.conversations(SecurityUtils.currentUserId(),page,size,keyword,unreadOnly);
    }
    @GetMapping("/conversations/overview")
    public InboxOverview overview() {
        return messages.inboxOverview(SecurityUtils.currentUserId());
    }
    @GetMapping("/conversations/{id}/summary")
    public Conversation summary(@PathVariable long id) {
        return messages.conversation(SecurityUtils.currentUserId(),id);
    }
    @GetMapping("/conversations/{id}")
    public History history(@PathVariable long id,@RequestParam(required=false) Long beforeId,@RequestParam(defaultValue="30") int size) {
        return messages.history(SecurityUtils.currentUserId(),id,beforeId,size);
    }
    @PostMapping("/conversations/{id}")
    public Message send(@PathVariable long id,@RequestBody @Valid SendMessage request) {
        return messages.send(SecurityUtils.currentUserId(),id,request);
    }
    @PostMapping("/conversations/{id}/read")
    public ResponseEntity<Void> read(@PathVariable long id,@RequestBody @Valid ReadMessages request) {
        messages.read(SecurityUtils.currentUserId(),id,request.throughId());return ResponseEntity.noContent().build();
    }
    @PostMapping(value="/conversations/{id}/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadedImage upload(@PathVariable long id,@RequestPart("file") MultipartFile file) {
        return images.upload(SecurityUtils.currentUserId(),id,file);
    }
    @GetMapping("/attachments/{id}")
    public ResponseEntity<FileSystemResource> image(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline; filename=message.jpg")
            .body(new FileSystemResource(images.readable(SecurityUtils.currentUserId(),id)));
    }
}
