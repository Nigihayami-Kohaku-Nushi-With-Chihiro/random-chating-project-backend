package random.chating.org.randomchatingproject.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import random.chating.org.randomchatingproject.entity.User;
import random.chating.org.randomchatingproject.service.CustomUserDetailsService;

@Slf4j
@Controller
@RequiredArgsConstructor
public class HomeController {

    private final CustomUserDetailsService userDetailsService;

    /**
     * 메인 페이지 - 로그인 상태에 따라 다른 화면 표시
     */
    @GetMapping("/")
    public String home(Model model) {
        // SecurityContext에서 직접 인증 정보 가져오기
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        User user = null;
        if (authentication != null &&
                authentication.isAuthenticated() &&
                authentication.getPrincipal() instanceof User) {
            user = (User) authentication.getPrincipal();
        }

        if (user == null) {
            log.info("비로그인 사용자 메인 페이지 접근");
            return handleGuestUser(model);
        }

        log.info("로그인 사용자 메인 페이지 접근: {}", user.getUsername());
        return handleAuthenticatedUser(user, model);
    }

    /**
     * 로그인 페이지
     */
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    /**
     * 회원가입 페이지
     */
    @GetMapping("/register")
    public String registerPage() {
        return "register";
    }

    /**
     * 비로그인 사용자 처리
     */
    private String handleGuestUser(Model model) {
        model.addAttribute("isAuthenticated", false);
        model.addAttribute("pageTitle", "랜덤 채팅 - 새로운 만남의 시작");
        model.addAttribute("totalUsers", userDetailsService.getTotalUsers());
        return "index";
    }

    /**
     * 로그인 사용자 처리
     */
    private String handleAuthenticatedUser(User user, Model model) {
        model.addAttribute("isAuthenticated", true);
        model.addAttribute("user", user);
        model.addAttribute("pageTitle", user.getUsername() + "님, 환영합니다!");
        model.addAttribute("username", user.getUsername());
        model.addAttribute("email", user.getEmail());
        model.addAttribute("gender", user.getGender());
        model.addAttribute("age", user.getAge());
        model.addAttribute("role", user.getRole());
        return "index";
    }
}