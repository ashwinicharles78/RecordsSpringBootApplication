package com.records.Records.service;

import com.records.Records.Entity.User;
import com.records.Records.model.UserModel;

public interface UserService {
    User registerUser(UserModel userModel);

    User getUserByPhone(String phoneNumber);
}
