package com.brewhaven.security;

import com.brewhaven.entity.User;
import com.brewhaven.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        if (identifier == null || identifier.trim().isEmpty()) {
            throw new UsernameNotFoundException("Identifier cannot be empty");
        }
        String clean = identifier.trim();

        // 1. Try username
        java.util.Optional<User> userOpt = userRepository.findByUsernameIgnoreCase(clean);
        // 2. Try email
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByEmailIgnoreCase(clean);
        }
        // 3. Try phone
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByPhone(clean);
        }
        // 4. Try name
        if (userOpt.isEmpty()) {
            userOpt = userRepository.findByNameIgnoreCase(clean);
        }
        // 5. Try identifier query
        if (userOpt.isEmpty()) {
            java.util.List<User> matches = userRepository.findByIdentifier(clean);
            if (matches.size() == 1) {
                userOpt = java.util.Optional.of(matches.get(0));
            } else if (matches.size() > 1) {
                throw new UsernameNotFoundException("Multiple accounts found with identifier: " + clean + ". Please use your mobile number.");
            }
        }

        User user = userOpt.orElseThrow(() ->
                new UsernameNotFoundException("User not found with identifier: " + identifier));
        return UserPrincipal.create(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));
        return UserPrincipal.create(user);
    }
}
