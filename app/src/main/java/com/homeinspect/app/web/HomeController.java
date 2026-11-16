package com.homeinspect.app.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
class HomeController {

    private final String authServerUrl;

    HomeController(@Value("${spring.security.oauth2.client.provider.home-inspect.issuer-uri}") String authServerUrl) {
        this.authServerUrl = authServerUrl;
    }

    @GetMapping("/")
    String home() {
        return "index";
    }

    /** Shown when the OIDC login fails (disabled account, no role...). Public page. */
    @GetMapping("/login-error")
    String loginError(Model model) {
        // The auth-server landing page lets the user log out there and sign in with another account
        model.addAttribute("authServerUrl", authServerUrl + "/");
        return "login-error";
    }
}
