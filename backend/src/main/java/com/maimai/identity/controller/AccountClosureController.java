package com.maimai.identity.controller;

import com.maimai.identity.dto.AccountClosureDtos.*;
import com.maimai.identity.service.AccountClosureService;
import com.maimai.identity.service.SessionAuthenticationService;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/me")
public class AccountClosureController {
    private final AccountClosureService closures;
    private final SessionAuthenticationService sessions;
    public AccountClosureController(AccountClosureService closures,SessionAuthenticationService sessions) {this.closures=closures;this.sessions=sessions;}
    @PostMapping("/closure") public ClosureResult request(@RequestBody @Valid ClosureRequest body,HttpServletRequest request,HttpServletResponse response) {
        var result=closures.request(body);sessions.logout(request,response);return result;
    }
}
