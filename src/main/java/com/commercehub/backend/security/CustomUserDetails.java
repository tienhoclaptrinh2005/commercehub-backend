package com.commercehub.backend.security;

import com.commercehub.backend.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.stream.Collectors;


@Getter
@AllArgsConstructor

public class CustomUserDetails  implements UserDetails {


    private final User user;
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {

        return user.getUserRoles().stream()
                .map(userRole -> new SimpleGrantedAuthority(userRole.getRole().getName()))
                .collect(Collectors.toList());

    }
    @Override
    public String getPassword(){
        return user.getPasswordHash();
    }
    @Override
    public String getUsername(){
        return user.getEmail();

    }
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    @Override
    public boolean isEnabled() {
        return "ACTIVE".equals(user.getStatus());
    }

}
