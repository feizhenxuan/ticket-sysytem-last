package com.alipay.ticketbacked.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Forward frontend routes to the bundled SPA entry while leaving /api untouched.
 */
@Controller
public class SpaController {

    @RequestMapping({
            "/login",
            "/profile",
            "/pay/return",
            "/chat",
            "/app",
            "/app/**"
    })
    public String forwardFrontendRoutes() {
        return "forward:/index.html";
    }
}
