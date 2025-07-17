package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.util.ObjectUtils;
import org.springframework.web.servlet.HandlerInterceptor;


public class LoginUserInterceptor implements HandlerInterceptor, Ordered {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserDTO user = UserHolder.getUser();
        if(user == null || ObjectUtils.isEmpty( user)) {
            response.setStatus(401);
            throw new RuntimeException("未登录");
        }

        return true;
    }

    @Override
    public int getOrder() {
        return 1;
    }
}
