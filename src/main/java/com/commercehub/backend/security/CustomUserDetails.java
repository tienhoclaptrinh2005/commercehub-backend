package com.commercehub.backend.security;

import com.commercehub.backend.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;

@Getter
@AllArgsConstructor

public class CustomUserDetails implements UserDetails{
    private  final User user;
    private  final Collection<? extends GrantedAuthority> authorities;
    private Long id;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() { return user.getPasswordHash(); }

    @Override
    public String getUsername() { return user.getEmail(); } // Dùng email để đăng nhập

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() {
        return "ACTIVE".equals(user.getStatus());
    }
    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }


}