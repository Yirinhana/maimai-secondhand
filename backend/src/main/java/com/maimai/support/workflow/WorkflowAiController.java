package com.maimai.support.workflow;

import com.maimai.common.security.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/workflows")
public class WorkflowAiController {
    private final WorkflowAiService service;
    public WorkflowAiController(WorkflowAiService service){this.service=service;}
    public record Request(@NotBlank String stage,@Positive Long resourceId,@Size(max=1400) String question,@NotBlank @Size(max=64) String requestKey){}
    @GetMapping("/availability") public WorkflowAiService.Availability availability(){return service.availability();}
    @PostMapping public WorkflowAiService.Result ask(@Valid @RequestBody Request request){return service.ask(request.stage(),request.resourceId(),request.question(),request.requestKey());}
    @GetMapping("/{id}") public WorkflowAiService.Result result(@PathVariable long id){return service.result(id,SecurityUtils.currentUserId());}
}
