package com.tcet.RestApi.service;

import com.tcet.RestApi.model.User;
import com.tcet.RestApi.modeldto.RegisterRequestDTO;
import com.tcet.RestApi.repositorys.UserRepository;
import com.tcet.RestApi.security.SecurityConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
@Service
public class UserService {

    @Autowired
   UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    public User registerUser(RegisterRequestDTO request){
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(
                passwordEncoder.encode(request.getPassword())
        );
        user.setRole(request.getRole());
        return userRepository.save(user);
    }

}
