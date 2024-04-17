package com.records.Records.service.impl;

import com.records.Records.Entity.User;
import com.records.Records.Repository.UserRepository;
import com.records.Records.model.UserModel;
import com.records.Records.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public User registerUser(UserModel userModel) {
        if(null != userRepository.findByEmail(userModel.getEmail())) {
            throw new RuntimeException("User Exists");
        }else  {
            User user = new User();
            user.setEmail(userModel.getEmail());
            user.setFirstName(userModel.getFirstName());
            user.setLastName(userModel.getLastName());
            user.setRoles(userModel.getRoles());
            user.setPassword(passwordEncoder.encode(userModel.getPassword()));
            user.setEnabled(true);

            userRepository.save(user);
            return user;
        }
    }

}
