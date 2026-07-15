package com.teaching.backend.auth.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestCallbackController {

    @GetMapping("/oauth2/redirect")
    public String testCallback(@RequestParam String accessToken) {
        return "로그인 성공!";
    }
}