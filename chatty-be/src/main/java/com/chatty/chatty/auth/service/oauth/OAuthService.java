package com.chatty.chatty.auth.service.oauth;

import static com.chatty.chatty.auth.exception.AuthExceptionType.UNSUPPORTED_LOGIN_PROVIDER;

import com.chatty.chatty.auth.controller.dto.TokenResponse;
import com.chatty.chatty.auth.controller.oauth.dto.SocialAuthResponse;
import com.chatty.chatty.auth.controller.oauth.dto.SocialLoginRequest;
import com.chatty.chatty.auth.controller.oauth.dto.SocialUserResponse;
import com.chatty.chatty.auth.entity.Provider;
import com.chatty.chatty.auth.exception.AuthException;
import com.chatty.chatty.auth.jwt.JwtUtil;
import com.chatty.chatty.user.entity.User;
import com.chatty.chatty.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthService {

    private final List<SocialLoginService> loginServices;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Transactional
    public TokenResponse signIn(SocialLoginRequest request, Provider provider) {
        SocialLoginService loginService = getLoginService(provider);
        log.info("request.code() : {}", request.code());
        log.info("loginService.getServiceName() : {}", loginService.getServiceName());
        SocialAuthResponse socialAuthResponse = loginService.getAccessToken(request.code());
        SocialUserResponse socialUserResponse = loginService.getUserInfo(socialAuthResponse.getAccess_token());
        log.info("socialUserResponse : {}", socialUserResponse.userEmail());
        log.info("socialUserResponse : {}", socialUserResponse.profileImage());

        Optional<User> optionalUser = userRepository.findByEmail(socialUserResponse.userEmail());
        User user;

        if (optionalUser.isEmpty()) {
            User newUser = User.builder()
                    .email(socialUserResponse.userEmail())
                    .isValid(true)
                    .loginType(loginService.getServiceName())
                    .profileImage(socialUserResponse.profileImage())
                    .build();
            user = join(newUser, loginService);
        } else {
            user = optionalUser.get();
        }
        log.info("user.getEmail() : {}", user.getEmail());
        log.info("user.getProfileImage() : {}", user.getProfileImage());

        String accessToken = jwtUtil.createAccessToken(user);
        String refreshToken = jwtUtil.createRefreshToken(user);
        return new TokenResponse(accessToken, refreshToken);
    }

    private SocialLoginService getLoginService(Provider provider) {
        for (SocialLoginService loginService : loginServices) {
            if (provider.equals(loginService.getServiceName())) {
                return loginService;
            }
        }
        throw new AuthException(UNSUPPORTED_LOGIN_PROVIDER);
    }

    private User join(User user, SocialLoginService loginService) {
        return userRepository.save(User.builder()
                .email(user.getEmail())
                .isValid(true)
                .loginType(loginService.getServiceName())
                .profileImage(user.getProfileImage())
                .build());
    }
}