package jar.controller;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jar.service.Dservice;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class Read {

    @Autowired
    private Dservice service;

    @GetMapping("/read")
    public Map<Object, Object> read() {
        return service.getAllStudents();
    }
}
