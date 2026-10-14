package com.maimai.governance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/governance")
public class GovernanceController {
    private final GovernanceService service;
    public GovernanceController(GovernanceService service){this.service=service;}
    public record Decision(@NotBlank String action,@NotBlank @Size(min=6,max=1000) String note) {}
    public record Pause(@NotNull Boolean paused) {}
    @GetMapping public GovernanceService.Overview overview(){return service.overview();}
    @GetMapping("/cases") public GovernanceService.Page<GovernanceService.CaseRow> list(@RequestParam(defaultValue="OPEN") String status,@RequestParam(required=false) String lane,@RequestParam(defaultValue="0") int page){return service.list(status,lane,page);}
    @GetMapping("/cases/{id}") public GovernanceService.Detail detail(@PathVariable long id){return service.detail(id);}
    @PostMapping("/cases/{id}/actions") public GovernanceService.Detail act(@PathVariable long id,@Valid @RequestBody Decision request){return service.action(id,request.action(),request.note());}
    @GetMapping("/cases/{id}/export") public ResponseEntity<byte[]> export(@PathVariable long id){return ResponseEntity.ok().header("Cache-Control","no-store").header("Content-Type","text/plain;charset=UTF-8").header("Content-Disposition","attachment; filename=maimai-review-"+id+".txt").body(("\uFEFF"+service.export(id)).getBytes(StandardCharsets.UTF_8));}
    @GetMapping("/jobs") public GovernanceService.Page<GovernanceService.Job> jobs(@RequestParam(defaultValue="QUEUED") String status,@RequestParam(defaultValue="0") int page){return service.jobs(status,page);}
    @PostMapping("/jobs/{id}/retry") public void retry(@PathVariable long id){service.retry(id);}
    @PostMapping("/pause") public void pause(@Valid @RequestBody Pause request){service.pause(request.paused());}
    @PostMapping("/scan-recent") public Map<String,Integer> scan(){return Map.of("queued",service.scanRecent());}
}
