package com.commercehub.backend.auth.mapper;
import com.commercehub.backend.auth.dto.request.RegisterRequest;
import com.commercehub.backend.user.entity.User;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class AuthMapper {
    public User toUserEntity(RegisterRequest request) {

        if (request == null) {
            return null;
        }
        User user = new User();
        user.setEmail(request.getEmail());
        user.setFullName(request.getFullName());
        user.setStatus("ACTIVE");
        user.setUserLevel(1);
        user.setAccumulatedSpent(BigDecimal.ZERO);
        user.setAccumulatedEarned(BigDecimal.ZERO);
        user.setIsEmailVerified(false);
        user.setIsPhoneVerified(false);

        return user;


    }


}
