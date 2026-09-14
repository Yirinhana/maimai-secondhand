package com.maimai.official;

import jakarta.validation.constraints.*;
import java.time.Instant;

public final class OfficialDtos {
    private OfficialDtos() {}
    public enum Category {NOTICE, GUIDE, SAFETY, ABOUT}
    public enum Status {DRAFT, PUBLISHED, WITHDRAWN}
    public record ArticleRequest(
            @NotBlank @Size(max=80) @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") String slug,
            @NotBlank @Size(max=120) String title,
            @NotBlank @Size(max=300) String summary,
            @NotBlank @Size(max=20000) String body,
            @NotNull Category category, @NotNull Status status) {
        public ArticleRequest {slug=trim(slug);title=trim(title);summary=trim(summary);body=trim(body);}
        private static String trim(String value) {return value==null?null:value.strip();}
    }
    public record ArticleSummary(long id,String slug,String title,String summary,Category category,
                                 Instant publishedAt,Instant updatedAt) {}
    public record ArticleDetail(long id,String slug,String title,String summary,Category category,
                                Instant publishedAt,Instant updatedAt,String body,String publisher) {}
    public record AdminArticle(long id,String slug,String title,String summary,String body,Category category,
                               Status status,Instant publishedAt,Instant updatedAt) {
        ArticleSummary summaryView() {return new ArticleSummary(id,slug,title,summary,category,publishedAt,updatedAt);}
        ArticleDetail detailView() {return new ArticleDetail(id,slug,title,summary,category,publishedAt,updatedAt,body,"麦麦官方");}
    }
}
