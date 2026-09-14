package com.maimai.official;

import com.maimai.official.OfficialDtos.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/official/articles")
@PreAuthorize("hasAnyRole('OPERATOR','SUPER_ADMIN')")
public class AdminOfficialController {
    private final OfficialArticleService articles;
    public AdminOfficialController(OfficialArticleService articles) {this.articles=articles;}
    @GetMapping public Page<AdminArticle> list(@RequestParam(required=false) Category category,
            @RequestParam(required=false) Status status,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size) {
        return articles.adminList(category,status,page,size);
    }
    @PostMapping public AdminArticle create(@Valid @RequestBody ArticleRequest request) {return articles.create(request);}
    @PutMapping("/{id}") public AdminArticle update(@PathVariable long id,@Valid @RequestBody ArticleRequest request) {return articles.update(id,request);}
}
