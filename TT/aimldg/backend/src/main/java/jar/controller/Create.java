package jar.controller;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jar.model.Student;
import jar.service.Dservice;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class Create {

    @Autowired
    private Dservice service;

    @PostMapping("/create")
    public Map<Object, Object> create(@RequestBody Student d) {
        return service.createStudent(d);
    }
}
