package com.htv.farmverse.identity.login;

import jakarta.servlet.http.HttpSession;

import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Trang đăng nhập (Thymeleaf). POST /login do filter form login của Spring Security xử lý. */
@Controller
public class LoginController {

    @GetMapping("/login")
    public String login(HttpSession session, Model model,
                        @RequestParam(name = "error", required = false) String error,
                        @RequestParam(name = LoginRateLimitFilter.LIMITED_PARAM, required = false) String limited) {
        Object failure = session.getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
        model.addAttribute("tenantRequired", failure instanceof TenantSelectionRequiredException);
        addLimited(model, limited);
        // Thymeleaf 3.1 không cho đọc ${param} trong thuộc tính th:aria-*: tính sẵn cờ ở đây.
        model.addAttribute("hasMessage", error != null || limited != null);
        return "login";
    }

    /** Bước 2 cho tài khoản bật TOTP (đã đăng nhập bằng mật khẩu). */
    @GetMapping(TotpLogin.PAGE)
    public String totp(Model model,
                       @RequestParam(name = "error", required = false) String error,
                       @RequestParam(name = LoginRateLimitFilter.LIMITED_PARAM, required = false) String limited) {
        addLimited(model, limited);
        model.addAttribute("hasMessage", error != null || limited != null);
        return "login-totp";
    }

    /**
     * Thông báo khi bị giới hạn theo IP. Giá trị trên URL do người ngoài đặt nên chỉ nhận số giây trong khoảng
     * hợp lý, còn lại bỏ qua; chuỗi hiển thị do server dựng.
     */
    private static void addLimited(Model model, String limited) {
        if (limited == null) {
            return;
        }
        int seconds = parseSeconds(limited);
        model.addAttribute("limited", seconds > 0
                ? "Thử quá nhiều lần. Hãy đợi khoảng " + seconds + " giây rồi thử lại."
                : "Thử quá nhiều lần. Hãy đợi một lúc rồi thử lại.");
    }

    private static int parseSeconds(String value) {
        try {
            int seconds = Integer.parseInt(value);
            return seconds > 0 && seconds <= 3600 ? seconds : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
