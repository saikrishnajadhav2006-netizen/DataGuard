package com.example.crypto.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.crypto.dto.LoginRequest;
import com.example.crypto.dto.SignupRequest;
import com.example.crypto.model.User;
import com.example.crypto.repository.UserRepository;

@Service
public class AuthService {

    @Autowired
    private UserRepository db;

    // Signup logic
    public Map<String, Object> signup(SignupRequest req) {
        Map<String, Object> res = new HashMap<>();

        if (db.existsByUsername(req.getUsername())) {
            res.put("msg", "Username already exists");
            res.put("status", 400);
            return res;
        }

        User u = new User();
        u.setUsername(req.getUsername());
        u.setPassword(req.getPassword());

        db.save(u);

        res.put("msg", "Signup Successful");
        res.put("status", 201);
        res.put("data", u);

        return res;
    }

    // Login logic
    public Map<String, Object> login(LoginRequest req) {
        Map<String, Object> res = new HashMap<>();
        Optional<User> data = db.findByUsername(req.getUsername());

        if (data.isPresent()) {
            User u = data.get();
            if (u.getPassword().equals(req.getPassword())) {
                res.put("msg", "Login Successful");
                res.put("status", 200);
                res.put("data", u);
            } else {
                res.put("msg", "Invalid Password");
                res.put("status", 401);
            }
        } else {
            res.put("msg", "User Not Found");
            res.put("status", 404);
        }

        return res;
    }
}