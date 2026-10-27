package com.htv.farmverse.identity.login;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/** Trang đăng nhập (Thymeleaf). POST /login do filter form login của Spring Security xử lý. */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(HttpSession session, Model model) {
        Object error = session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        model.addAttribute("tenantRequired", error instanceof TenantSelectionRequiredException);
        return "login";
    }

    /** Bước 2 cho tài khoản bật TOTP (đã đăng nhập bằng mật khẩu). */
    @GetMapping(TotpLogin.PAGE)
    public String totp() {
        return "login-totp";
    }
}
