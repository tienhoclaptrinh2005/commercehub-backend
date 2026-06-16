package com.commercehub.backend.security;

import com.commercehub.backend.user.entity.User;
import com.commercehub.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {

        User user = userRepository.findByEmail(identifier)
                .orElseGet(() -> userRepository.findByUsername(identifier)
                        .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy tài khoản với định danh: " + identifier)));

        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().toUpperCase()))
                .collect(Collectors.toList());

        return new CustomUserDetails(user, authorities,user.getId());
    }
}