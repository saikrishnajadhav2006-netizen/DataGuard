package jar.controller;

import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jar.model.Student;
import jar.service.Dservice;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "*")
public class Update {

    @Autowired
    private Dservice service;

    @PutMapping("/update/{id}")
    public Map<Object, Object> update(@PathVariable Long id, @RequestBody Student d) {
        return service.updateStudent(id, d);
    }
}
