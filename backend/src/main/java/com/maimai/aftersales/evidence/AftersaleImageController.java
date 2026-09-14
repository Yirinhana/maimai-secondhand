package com.maimai.aftersales.evidence;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;

@RestController
@RequestMapping("/api/v1/aftersales")
public class AftersaleImageController {
    private final AftersaleImageService images;
    public AftersaleImageController(AftersaleImageService images) {this.images=images;}
    @GetMapping("/{id}/images") public List<AftersaleImageService.ImageView> list(@PathVariable long id) {return images.list(id);}
    @PostMapping(value="/{id}/images",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public AftersaleImageService.ImageView upload(@PathVariable long id,@RequestPart("file") MultipartFile file) {return images.upload(id,file);}
    @GetMapping("/images/{id}") public ResponseEntity<FileSystemResource> image(@PathVariable UUID id) {
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline; filename=evidence.jpg")
                .body(new FileSystemResource(images.readable(id)));
    }
}
