package com.petadoption.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class TestController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/test-db")
    public Map<String, Object> testDatabase() {
        Map<String, Object> response = new HashMap<>();
        try {
            List<String> tables = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name;",
                String.class
            );
            response.put("status", "SUCCESS");
            response.put("message", "Connected successfully to Supabase PostgreSQL!");
            response.put("tablesFound", tables);
        } catch (Exception e) {
            response.put("status", "ERROR");
            response.put("message", "Connection failed: " + e.getMessage());
        }
        return response;
    }
}
