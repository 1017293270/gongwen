package com.gongwen.assistant.security;

import com.gongwen.assistant.organization.UserAccountRepository;
import com.gongwen.assistant.organization.UserAccountRecord;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class GongwenUserDetailsService implements UserDetailsService {
    private final UserAccountRepository userAccountRepository;

    public GongwenUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserAccountRecord user = userAccountRepository.findActiveByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return GongwenUserPrincipal.from(user);
    }
}
