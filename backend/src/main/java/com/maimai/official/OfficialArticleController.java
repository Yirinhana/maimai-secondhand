package com.maimai.official;

import com.maimai.official.OfficialDtos.*;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/official/articles")
public class OfficialArticleController {
    private final OfficialArticleService articles;
    public OfficialArticleController(OfficialArticleService articles) {this.articles=articles;}
    @GetMapping public Page<ArticleSummary> list(@RequestParam(required=false) Category category,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="12") int size) {
        return articles.list(category,page,size);
    }
    @GetMapping("/{slug}") public ArticleDetail detail(@PathVariable String slug) {return articles.detail(slug);}
}
